# マクロをつかうべきかどうなのか

シリアルポート経由で[zlog]()というデバイスとの通信をするツールを作って
いるのですが、その途中で、処理をマクロにすべきかどうかいろいろ考えたの
で、そのときのことを。

本当は、このツールそのものを題材に使用と思っていたのですが、案の定、挫
折しました。

## zlogとの通信について

zlogはラジコンに積むように作られた小型の高度計で、起動すると一定時間ご
とに高度(=気圧)を測定して、それを記憶してくれます。取ったデータは、シ
リアルポート経由でやりとりできるようになっています。

プロトコルはsnifferで解析しながらなのですが、わかる範囲では簡単で、1バ
イトのコマンドに、必要であれば引数を付けて送ります。結果がある場合は結
果が返ってきます。

```
例)  
  送信 : v
  応答 : ZLOG\rMOD3\rv3-4\rFeb 07 2006\r
```

## clojureでシリアル通信

JAVAでシリアル通信と言えば、以前仕事でモデムの制御に使った
[RXTX](http://rxtx.qbang.org/wiki/index.php/Main_Page)が思いつきます。
今回もこれを使おうと思ったのですが調べてみると、すでにこのRXTXのラッパーがありました。その名も
[serial-port](https://github.com/samaaron/serial-port)とずばりの名前で
す。今回はこれを使うことにしました。

基本的にはお決り通りopenして通信して終ったらcloseするようになってい
て、応答メッセージの受け取りは、コールバック関数を登録するよう
になっています。 (やろうと思えば、低レベルのI/Fも提供されています)

今回のツールでは、コマンドごとにopen/closeすることにし
ました。 これは、基本的にzlogのコマンドが独立していていつでも送信でき
ることと、使いかたからしてデバイスそのものにアクセスするのはそれほど大
くないので、コネクションの管理をしなくて済むようにしたかったためです。

つまり、zlogと通信する場合、
- openする
- バッファを用意する
- コールバック関数を登録する
- コマンドを送信する
- バッファの内容を取得する
- closeする
という処理をすることになります。

## 実装編

### その1 関数編
まずは普通に関数化してみました。

```clojure
(defn z-open [port-name] ...)
(defn z-close [conn] ...)
(defn z-register-callback [conn] ...)
(defn z-send-command [conn command & args] ...)
```

こんな関数たちを作って、やりたいことがあれば、

```clojure
(defn get-version-info [port-name]
  (let [connection (z-open port-name)
        buff (atom [])]
    (do (z-register-callback #(swap! buff conj %))
        (z-send-command connection :version)  ;★
        (wait-for-data-end)
        (z-close connection))
    (into [] @buff)))
```

このように関数をつくることになります。  
でも、この方法だと、★のところが違うだけの関数がたくさんできてしまいま
す。

そんなときはマクロですよ。

### その2 マクロ編

同じような関数がたくさんできてしまうようなときはマクロにすることを考えます。

#### 作ってみる

さきほどの関数をすなおにマクロにするとこうなります。

```clojure
(defmacro send-and-get-01 [port-name & commands]
  '(let [connection# (z-open ~port-name)
        buff# (atom [])]
    (dorun (z-register-callback #(swap! buff# conj %))
           ~@commands
           (wait-for-data-end)
           (z-close connection#))
    (into [] @buff#)))
```

★の付いていたところを、マクロの引数にしてやります。
これでさっきの関数を実装してみると、

```clojure
(defn get-version-info [port-name]
   (send-and-get-01 port-name
                    (z-send-command ??? :version))  ;!!!
```

待て待て。  
この関数の???のところには、引数としてopenの返り値のconnectionが必要なのです。
ところが、この値は、マクロの中に入ってしまっているので、そとからは見え
ないので指定できません。

うーん。  
アナフォリックマクロの出番ですね。

その前に

#### アナフォリックマクロ = 変数の捕捉

lispにはマクロの引数の補足という問題があって、やっかいな問題なので
すが、clojureではマクロ内に普通のシンボルを書けないようにしてそれを
防止しています。

```clojure
(defmacro ouch [a b]
  `(let [x (range ~a)]
     (map ~b x)))

user> (ouch 10 double)
CompilerException java.lang.RuntimeException: Can't let qualified name: user/x, compiling:(NO_SOURCE_PATH:1:1) 

user> (macroexpand-1 '(ouch 10 int))
(clojure.core/let [user/x (clojure.core/range 10)]
  (clojure.core/map int user/x))
```

マクロのなかはletで束縛されるシンボルとして x を使っています。このマ
クロは定義できますが、使おうとすると例外が発生します。この例外は、
qualifiedのシンボルはletに使えないというものですが、 
マクロを展開してみると、user/x のようになっているのがわかります。

マクロの中で#を付けることで、使えるようになります。

```clojure
(defmacro yaa [a b]
  `(let [x# (range ~a)]
     (map ~b x#)))

user> (yaa 10 double)
(0.0 1.0 2.0 3.0 4.0 5.0 6.0 7.0 8.0 9.0)

user>(macroexpand-1 '(yaa 10 double))
(clojure.core/let [x__18343__auto__ (clojure.core/range 10)]
  (clojure.core/map double x__18343__auto__))

```

このように、x だったところに、 x__18343__auto__ が割り当てられています。
これは、マクロを展開するときに、他と衝突しない = 変数を捕捉しない変数
として自動的に生成されるシンボルです。

で、この機能を無効にして、むりやり捕捉できるようにしたいわけですが、、こう書きます。

```clojure
(defmacro wao [a b]
  `(let [~'x (range ~a)]
     (map ~b ~'x)))

user> (wao 10 double)
(0.0 1.0 2.0 3.0 4.0 5.0 6.0 7.0 8.0 9.0)

user> (macroexpand-1 '(wao 10 double))
(clojure.core/let [x (clojure.core/range 10)]
  (clojure.core/map double x))
```

展開したものを見るとわかるとおり、xがそのまま出てきます。これで、この
マクロに与えたものと内部のxを同じものとすることができることになります。

ちなみに、これが問題になるのはたとえばこんなことになるからです。

```clojure
(def x 10)

user> (yaa 10 #(+ % x))
(10 11 12 13 14 15 16 17 18 19)

user> (wao 10 #(+ % x))
ClassCastException clojure.lang.LazySeq cannot be cast to java.lang.Number  clojure.lang.Numbers.add (Numbers.java:126)
```

yaaは思ったとおりの動作をしていますが、waoは例外を吐いています。このと
き、外で設定したxとマクロの中のxが衝突してしまっているのです。

この例のように予期せずに発生してしまうとやっかいではありますが、計画的
に使えば、便利に使えます。

#### アナフォリックする

マクロの中で定義されている値をマクロの外から参照できるようにしたものが、
アナフォリックマクロです。 (チョット違うかな?)

詳しいことは On Lisp を読んでください。

さきほどのマクロと関数をもう一度見てみます。

```clojure
(defmacro send-and-get-01 [port-name & commands]
  '(let [connection# (z-open ~port-name)
         buff# (atom [])]
    (do (z-register-callback #(swap! buff# conj %))
        ~@commands
        (wait-for-data-end)
        (z-close connection#))
    (into [] @buff#)))

(defn get-version-info [port-name]
   (send-and-get-01 port-name
                    (z-send-command ??? :version))  ;!!!
```

get-version-info関数の???のところに、マクロの中身の connection を使えるようにしたいわけです。  
おわかりですね。  
こうします。


```clojure
(defmacro send-and-get-02 [port-name & commands]
  '(let [~'a-con (z-open ~port-name)
         buff# (atom [])]
    (do (z-register-callback #(swap! buff# conj %))
        ~@commands
        (wait-for-data-end)
        (z-close ~'a-con))
    (into [] @buff#)))

(defn get-version-info [port-name]
   (send-and-get-02 port-name
                    (z-send-command a-con :version))  ;!!!
```

これでOK。 connection というのは一般的すぎる名前なので、アナフォラであ
ることがわかるような a-con にしてみた。

これでよし...

ではなくて、やっぱり、「可能であればマクロは使うな」であ
るし、まして、アナフォリックなものなどは... ということで、別解を考えて
みることにしました。

### その3 アナフォリックでないマクロ

さて、もともとのマクロの問題は、マクロ内部にのみある connection を、引
数の関数に渡せないということです。でも、相手はマクロです。 渡したものは、
部品として使われるのでやろうと思えばその構造も変えられます。  
だったら、あとから connection を関数に挿入してしまえばいいじゃないか!

```clojure
(defmacro send-and-get [port-name & commands]
  '(let [connection# (z-open ~port-name)
         buff# (atom [])]
    (do (z-register-callback #(swap! buff# conj %))
        (-> connection#      ;★
            ~@commands)
        (wait-for-data-end)
        (z-close connection#))
    (into [] @buff#)))

(defn get-version-info [port-name]
   (send-and-get port-name
                 (z-send-command ,,, :version))  ;!!!
```

はい。このように、★のところで -> マクロを使って、渡された関数たちの1番目の引数に
connectionを入れて評価するようにしてみました。

この方式だと、アナフォリックではなくなるのですっきりします。

でも、渡せる関数がかならず1番目にconnectionを受け取るようなものでなくてはな
らないという制約が付いて、その関数の1番目の引数を指定しないようにしな
くてはなりません。
とはいえ、これって、 -> マクロとかの制約と同じなので、たいしたことでは
ないのかも。

## まとめ

今回はマクロを作ったあとにあれこれいじることになったので、記事にしてみ
ました。

実際のツールでは、例外の扱いなど入れたりして、こんな物になっています。

```clojure
(ns zlog-view.zlog
  (require [serial-port :as sp]))

(def *data-timeout* 3000)

(defmacro command-and-read [port-id & commands]
  `(let [conn# (sp/open ~port-id)
         buff# (atom [])]
     (try
       (sp/on-byte conn# #(swap! buff# conj %))
       (-> conn#
           ~@commands)
       (loop [last-count# (count @buff#)]
         (Thread/sleep 3000)
         (when (> (count @buff#) last-count#)
           (recur (count @buff#))))
       (into [] @buff#)
       (finally
         (sp/remove-listener conn#)
         (sp/close conn#)))))

(defn get-device-info [port-name]
  (->> (command-and-read port-name
                         (send-command :version))
       (map char ,,)
       (apply str ,,)
       (#(clojure.string/split % #"\r\n*") ,,)))
```


何かのお役にたてば。




