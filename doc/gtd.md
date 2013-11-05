zlogデータビューアを作ろう
==========================

# はじめに
## zlogとは
そこそこ長いことラジコングライダーも趣味としております。グライダーなの
で動力が無く、上昇気流を利用して高度を獲得することが大きな目的になるわ
けですが、練習時のフライトの評価のために、機体に高度計を積んで飛行の状
況のログを取り、後で評価するということをやります。  
その高度計の1つがこの[zlog](http://www.hexpertsystems.com/zlog/)です。
メインで飛ばしている機体は一番小さなクラスの機体なのですが、これは重量
が数gのもので、しかも、ディスプレイが付いているのでフィールドでの情報
の確認ができるのが特徴です。  
使っているのは、だいぶ前にリリースされたMOD3というバージョンですが、す
でに充分な性能を持っています。

## データビューア
zlogは取得したデータを内部に保存してくれていて、PCにつないで取り出して、
グラフとして表示することができるのですが、そのツールはWindows用しか提
供されていません。  
JAVAで作ってあればいいのになぁ、と思ってましたが、アドベントカレンダー
の題材とすることで作ってみることにしました。

たぶん、間に合わないでしょうが、行けるところまで行きいます。

## プロトコル
zlogにはUSB-シリアル通信インターフェースが用意されていますが、データを
取りだす方法を調べる必要があります。開発元に問い合わせればいいのでしょ
うが、サイトで提供されているサードパーティーのツールに、Perlで作られ
たものがあったので、これを解析することにしました。いくつかの機能は欠け
ていますが、今回の目的には充分です。

解析の結果は、doc/zlog_protocol.org にあります。
基本的に、文字1つもしくは文字1つ+数(1byte)のコマンドを送信することで命
令し、応答があるもは、それぞれ決ったフォーマットで帰ってくるようです。

# ソフトウェア
## シリアルインターフェース
JAVAでシリアル通信と言えば、以前仕事でモデムの制御に使った
[RXTX](http://rxtx.qbang.org/wiki/index.php/Main_Page)が思いつきます。
clojureからこれを使うという手もあるのですが、探してみると、すでにこの
RXTXのラッパーがありました。その名も
[serial-port](https://github.com/samaaron/serial-port)とずばりの名前で
す。今回はこれを使うことにします。

## 試験ツール
シリアルインターフェースを持つツールを作るときには、インターフェースを
疑似ることができるととてもに楽です。作ったものが意図した通りに動いてい
るのかどうかを、実物を使わないとわからないのではやりにくいのです。特に、
今回のように相手(zlog)の仕様が不明瞭な場合には特に必須とも言えます。

そんな時に使っているのが、
[VSPE](http://eterlogic.com/Products.VSPE.html)という、シリアルポート
のエミュレータです。これを使うと、
    ビューアー (COMx)<-- VSPE -->(COMy)  TeraTerm
のようにつないで、TeraTermをzlogのかわり使うことができます。

## GUI
clojureでGUIと言えば、[seesaw](https://github.com/daveray/seesaw)です。
JAVA swingのラッパーで、作者の主張によれば、swingの知識が無くてもGUIを
構築することができるとのこと。実際、僕もswingの経験があるわけではない
のですが、これまで2つほど小さなツールを作ってみて、それほど困難を感じ
ることはありませんでした。  
ただ、Tcl/TkでのGUIツールの経験があって、コマンドベースでのGUIツールの
コツは知っていたということもあるので、VBなどのようなグラフィカルなGUI
ツールしか知らないと、難しく感じるかもしれません。

## グラフ作成
JAVAでグラフで探してみると、
[JFreeChart](http://www.jfree.org/jfreechart/)というのが見つかります。
表示したグラフは、ズームしたりパンしたりする必要があるのですが、サポー
トされているようです。

有名なライブラリのようなので、clojureのラッパーがあるかと思ったのです
が、見つかりませんでした。


# プロジェクト開始
さて、情報は集まったので、作り始めましょう。

家のPCはUbuntu、仕事のPCはWin7という環境ですが、Win7には
[MinGW](http://www.mingw.org/)とGit・emacsが入れてあって、emacsの
eshellが使えるようにしてあるので、基本的に同様の操作感を演出しています。

## プロジェクト準備
### Leiningenのプロジェクトを作る
作業用のディレクトリで以下のコマンドを実行します。`zlog_view`はプロジェクト名です。

    lein new zlog_view

そうすると、Leiningenのプロジェクトができます。

    zlog_view
    |   .gitignore
    |   project.clj
    |   README.md
    |   
    +---doc
    |       intro.md
    |       
    +---resources
    +---src
    |   \---zlog_view
    |           core.clj
    |           
    +---target
    |           
    \---test
       

### 必要なライブラリの取得
プロジェクトディレクトリ直下にある、project.cljを編集して、
現状使うことがわかっている`serial-port`と`seesaw`と`JFreeChart`を追加
します。ついでに他のところもちょっと修正。

```clojure
(defproject zlog_view "0.1.0"
  :description "zlog data viewer"
  :url "https://github.com/ypsilon-takai"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [serial-port "1.1.2"]
                 [seesaw "1.4.4"]
                 [org.jfree/jfreechart "1.0.15"]])
```
修正したら、

    lein deps

とやって、必要なライブラリをローカルのリポジトリに落しておきます。

### gitで管理する
ソースコードの管理には`git`を使います。

`zlog_view`ディレクトリで、

    git init
    git add .
    git commit -m"newly created"

とやります。 emacsなので、実際は`magit`使ってますけどね。

### GitHubに入れる
どこにいてもゴソゴソといじれるように、GitHubに入れておきます。

GitHubのサイトで「New Repoository」ボタンを押して新しいリポジトリを作
ります。名前はローカルに作ったものと同じ「zlog_view」にします。

ローカルのファイルをGitHubに入れます。`zlog_view`ディレクトリで、

    git remote add origin 









