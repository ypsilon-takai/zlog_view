# マクロをつかうべきかどうなのか

シリアルポート経由で[zlog]()というデバイスとの通信をするツールを作って
いるのですが、その途中で、処理をマクロにすべきかどうかいろいろ考えたの
で、そのときのことを。

## zlogとの通信について

zlogというのはラジコンに積むように作られた小型の高度計で、起動すると一定時間ご
とに高度(=気圧)を測定して、それを記憶してくれます。取ったデータは、シ
リアルポート経由でやりとりできるようになっていて、Windows用のプログラ
ムが正式版として提供されています。

プロトコルは公開されていなくて、snifferで解析しながら作っているのですが、
わかる範囲ではしくみは簡単で、コマンドは1バイトで、必要であれば引数が
付きます。結果がある場合は結果が返ってきます。

```
例)  
  送信 : v
  応答 : ZLOG<cr>MOD3<cr>v3-4<cr>Feb 07 2006<cr>
```

## clojureでシリアル通信

JAVAでシリアル通信と言えば、以前仕事でモデムの制御に使った
[RXTX](http://rxtx.qbang.org/wiki/index.php/Main_Page)が思いつきます。
今回もこれを使おうと思ったのですが、調べてみると、すでにこのRXTXのラッパーが
ありました。その名も
[serial-port](https://github.com/samaaron/serial-port)とずばりの名前で
す。今回はこれを使うことにしました。

基本的にはお決まり通りopenして通信して終ったらcloseするようになってい
て、送信はsendコマンドで、応答メッセージの受け取りは、コールバック関数
が登録できるようになっています。
また、低レベルのI/Fも提供されています。

今回のツールでは、コマンドごとにopen/closeすることにしました。
これは、基本的にzlogのそれぞれのコマンドが独立であっていつでも送信でき
ることと、使い方からしてデバイスそのものにアクセスするのはそれほど多
くないので、コネクションの管理をしなくて済むようにしたかったためです。

つまり、
- openする
- バッファを用意する
- コールバック関数を登録する
- コマンドを送信する
- バッファの内容を取得する
- closeする
という処理を、zloとやりとりするたびにすることになります。

## 実装編

_注意_
以下の例のコードは、説明のための疑似コード相当なので、動作の確認はして
ません。動かないこともままあるでしょう。 そんなときは、ご指摘ください。

### その1 関数編
まずはそれぞれの処理を関数化して使うことを考えました。

```clojure
(defn z-open [port-name] ...)
(defn z-close [conn] ...)
(defn z-register-callback [conn] ...)
(defn z-send-command [conn command & args] ...)
```

これらの関数たちを作って、やりたいことがあれば、

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
ところが、この方法だと、★のところが違うだけの関数をいくつも作らなくて
はならなくなります。
では、この処理をまとめた関数を作りましょうか。それでもいいのですが、関
数にすると、★のところの自由度が下ってしまうのです。(結果的には今回の
使いかたでは関数で充分だったのですが。)

そんなときはマクロですよ。

### その2 マクロ編

同じような関数がたくさんできてしまうようなときはマクロにすることを考えます。

#### 作ってみる

さきほどの関数をすなおにマクロにするとこうなります。

```clojure
(defmacro send-and-get-01 [port-name & commands]
  `(let [connection# (z-open ~port-name)
         buff# (atom [])]
    (dorun (z-register-callback #(swap! buff# conj %))
           ~@commands               ;★
           (wait-for-data-end)
           (z-close connection#))
    (into [] @buff#)))
```

* defnをdefmacroに書き換える。
* 関数名を汎用の名前に変える。
* まずは本体全部をsyntax-quote(`)に入れる。
* letにある変数(？)には#を付ける。
* 関数ごとに異なるところを引数で渡すようにする。
* 引数にはチルダ(~ か ~@)を付ける。

これでさっきの関数を実装してみると、

```clojure
(defn get-version-info [port-name]
   (send-and-get-01 port-name
                    (z-send-command ??? :version))  ;!!!
```

このようになります。
待て待て。  
この関数の???のところには、引数としてopenの返り値のconnectionが必要なのです。
ところが、この値は、マクロの中に隠れてしまっていて、外からは見えません。
これでは、？？？のところに入れられません。

うーん。  
これは、アナフォリックマクロの出番ですね。

その前に、アナフォリックマクロの背景について。
単に書いてみたかっただけ。

#### 変数の捕捉

lispにはもともとマクロの引数の補足という問題があって、やっかいな問題で、
lispでマクロを書くときは、引数が捕捉されてしまわないように気を付けなけ
ればならないことになっています。
clojureではマクロ内に普通のシンボルを書けないようにしてそれを
自動的に防止しています。

たとえば、以下のマクロは、emacs lispで書いたものです。
マクロにするような物ではないのですが、例として。

```elisp
(defmacro a+2b (a b)
  `(let ((x (* ,b 2)))
     (+ ,a x)))
```

実行すると、

```
(a+2b 2 3)
;-> 8
```

のように正しく動いていますが、こんな場合は正常に動作しません。

```
(setq x 10)
(a+2b (+ x 3) 2)
;-> 11
```

なぜ、13+2*2=17にならないのかと言うと、マクロを展開してみるとわかりま
す。

```
(macroexpand '(2a+b (+ x 3) 2))
(let ((x (* 2 2)))
    (+ (+ x 3) x))
```

定義通り、aの場所が(+ x 3)に、bの場所が2に置き換わっています。
ここで、注目すべきは、xです。 (+ x 3)のxは意図としては、外で設定されて
いる、10を持っているxであるはずなのですが展開後は、letで置き換えられ
てしまって、2を持つことになってしまっていいます。
これが、変数の捕捉です。

このようなことが普通に起こるとしたら、マクロを思ったとおりに使うために
は、そのマクロ内部で使われている変数が何なのかを知っていないとそのマク
ロが使えないということになってしまいます。

lispでは、マクロを作るときにgensymという関数を使ってこれを回避するのが一般的です。
使うのはちょっと面倒なのですが、clojureでは手軽な方法で対応でき
るようになっています。

先程の例をclojureで書いてみます。まずは、何も考えずに書くと、

```clojure
(defmacro a+2b [a b]
  `(let [x (* ~a 2)]
     (+ ~b x)))

user> (a+2b 2 3)
CompilerException java.lang.RuntimeException: Can't let qualified name: user/x, compiling:(NO_SOURCE_PATH:1:1)

user> (macroexpand-1 '(a+2b 2 3))
(clojure.core/let [user/x (clojure.core/* 2 2)]
    (clojure.core/+ 3 user/x))

```
定義はできますが、使おうとすると例外が発生します。
この例外は、qualifiedのシンボルはletに使えないというものですが、 
マクロを展開してみると、xのところが、ネームスペース付きのuser/x の
ようになっているのがわかります。

clojureでは予期しない変数の捕捉を防ぐために、マクロ内部での変数の扱い
をこのようにしています。

マクロの中で変数を使うためには、後ろに#を付けます。

```clojure
(defmacro a+2b [a b]
  `(let [x# (* ~a 2)]
     (+ ~b x#)))

user> (a+2b 2 3)
8

(macroexpand-1 '(a+2b 2 3))
(clojure.core/let [x__1996__auto__ (clojure.core/* 2 2)]
    (clojure.core/+ 3 x__1996__auto__))

```

このように、x だったところに、 x__1996__auto__ が割り当てられています。
これは、マクロを展開するときに、他と衝突しない = 変数を捕捉しない変数
として自動的に生成されたシンボルです。

#### 変数の捕捉の積極的利用 = アナフォリックマクロ

変数の捕捉はやっかいな問題の原因にもなりますが、上手く使うと便利なマク
ロを作ることもできます。
この、マクロの中で定義されている値をマクロの外から参照できるように
したものを、アナフォリックマクロと呼びます。

アナフォリックマクロの話は、とても深ーい話になるのですが、ここで書くには僕の力が不足していますので、
[On Lisp](http://www.amazon.co.jp/On-Lisp-%E3%83%9D%E3%83%BC%E3%83%AB-%E3%82%B0%E3%83%AC%E3%82%A2%E3%83%A0/dp/4274066371)
を読んでみてください。

clojureでアナフォリックマクロを作るには、シンボル名を名前空間で解
決してしまうのを回避しなければなりません。

シンボルの前に `~'` を付けるとこれを回避できます。

```clojure
(defmacro a+2b [a b]
  `(let [~'x (* ~b 2)]
     (+ ~a ~'x)))

user> (macroexpand-1 '(a+2b 2 3))
(clojure.core/let [x (clojure.core/* 3 2)] (clojure.core/+ 2 x))
```

展開したものを見るとわかるとおり、xがそのまま出てきます。これで、この
マクロの中のxに外部からアクセスできることになります。


#### アナフォリックする

さて、本題に戻ります。さきほどのマクロと関数をもう一度見てみます。

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
connectionを外から見えるようにすればいいわけですから、こうします。

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

これで一件落着。

...ではなくて、やっぱり、「可能であればマクロは使うな」であ
るし、まして、アナフォリックなものなどは... ということで、
アナフォリックにしない案を考えてみることにしました。

### その3 アナフォリックでないやつ。

もともとのマクロの問題は、マクロ内部にのみある connection を引
数の関数に渡せないということです。でも、相手はマクロです。 渡したものは、
部品として使われるので、その気になれば構造だって変えてしまえます。  
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
くてはなりません。また、ifなどの制御構造を入れて状況によって処理を分岐
するなどのこともできなくなります。

アナフォリックマクロが強力だってことですね。


## まとめ

今回はマクロを作ったあとにあれこれいじることになったので、記事にしてみ
ました。

また、openしてcloseする物を扱うものなので、本来であれば、with-openマク
ロと同様の作りにすべきかなとか、そもそも、with-openで動くべきなんじゃ
ないかとかいろいろありますが、当初の目的は達したので、これでよしとしま
す。
このあたりについてのご指摘をいただけるとありがたいです。

### 実際に作ったもの

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

### 関数化

「可能であればマクロは使うな」という観点からすると、可能であれば関数化
すべきではあります。
今回のツールの場合、折角作ったんだからということで、マクロのままにして
しまいますが、最終的に渡すコマンドは1つだけなので、関数でも対応できると思います。


ここまでです。 何かのお役にたてば。




