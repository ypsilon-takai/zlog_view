# �}�N���������ׂ����ǂ��Ȃ̂�

�V���A���|�[�g�o�R��[zlog]()�Ƃ����f�o�C�X�Ƃ̒ʐM������c�[���������
����̂ł����A���̓r���ŁA�������}�N���ɂ��ׂ����ǂ������낢��l������
�ŁA���̂Ƃ��̂��Ƃ��B

�{���́A���̃c�[�����̂��̂��ނɎg�p�Ǝv���Ă����̂ł����A�Ă̒�A��
�܂��܂����B

## zlog�Ƃ̒ʐM�ɂ���

zlog�̓��W�R���ɐςނ悤�ɍ��ꂽ���^�̍��x�v�ŁA�N������ƈ�莞�Ԃ�
�Ƃɍ��x(=�C��)�𑪒肵�āA������L�����Ă���܂��B������f�[�^�́A�V
���A���|�[�g�o�R�ł��Ƃ�ł���悤�ɂȂ��Ă��܂��B

�v���g�R����sniffer�ŉ�͂��Ȃ���Ȃ̂ł����A�킩��͈͂ł͊ȒP�ŁA1�o
�C�g�̃R�}���h�ɁA�K�v�ł���Έ�����t���đ���܂��B���ʂ�����ꍇ�͌�
�ʂ��Ԃ��Ă��܂��B

```
��)  
  ���M : v
  ���� : ZLOG\rMOD3\rv3-4\rFeb 07 2006\r
```

## clojure�ŃV���A���ʐM

JAVA�ŃV���A���ʐM�ƌ����΁A�ȑO�d���Ń��f���̐���Ɏg����
[RXTX](http://rxtx.qbang.org/wiki/index.php/Main_Page)���v�����܂��B
�����������g�����Ǝv�����̂ł������ׂĂ݂�ƁA���łɂ���RXTX�̃��b�p�[������܂����B���̖���
[serial-port](https://github.com/samaaron/serial-port)�Ƃ��΂�̖��O��
���B����͂�����g�����Ƃɂ��܂����B

��{�I�ɂ͂�����ʂ�open���ĒʐM���ďI������close����悤�ɂȂ��Ă�
�āA�������b�Z�[�W�̎󂯎��́A�R�[���o�b�N�֐���o�^����悤
�ɂȂ��Ă��܂��B (��낤�Ǝv���΁A�჌�x����I/F���񋟂���Ă��܂�)

����̃c�[���ł́A�R�}���h���Ƃ�open/close���邱�Ƃɂ�
�܂����B ����́A��{�I��zlog�̃R�}���h���Ɨ����Ă��Ă��ł����M�ł�
�邱�ƂƁA�g���������炵�ăf�o�C�X���̂��̂ɃA�N�Z�X����̂͂���قǑ�
���Ȃ��̂ŁA�R�l�N�V�����̊Ǘ������Ȃ��čςނ悤�ɂ������������߂ł��B

�܂�Azlog�ƒʐM����ꍇ�A
- open����
- �o�b�t�@��p�ӂ���
- �R�[���o�b�N�֐���o�^����
- �R�}���h�𑗐M����
- �o�b�t�@�̓��e���擾����
- close����
�Ƃ������������邱�ƂɂȂ�܂��B

## ������

### ����1 �֐���
�܂��͕��ʂɊ֐������Ă݂܂����B

```clojure
(defn z-open [port-name] ...)
(defn z-close [conn] ...)
(defn z-register-callback [conn] ...)
(defn z-send-command [conn command & args] ...)
```

����Ȋ֐�����������āA��肽�����Ƃ�����΁A

```clojure
(defn get-version-info [port-name]
  (let [connection (z-open port-name)
        buff (atom [])]
    (dorun (z-register-callback #(swap! buff conj %))
           (z-send-command connection :version)  ;��
           (wait-for-data-end)
           (z-close connection))
    (into [] @buff)))
```

���̂悤�Ɋ֐������邱�ƂɂȂ�܂��B  
�ł��A���̕��@���ƁA���̂Ƃ��낪�Ⴄ�����̊֐�����������ł��Ă��܂���
���B

����ȂƂ��̓}�N���ł���B

### ����2 �}�N����

�����悤�Ȋ֐�����������ł��Ă��܂��悤�ȂƂ��̓}�N���ɂ��邱�Ƃ��l���܂��B

#### ����Ă݂�

�����قǂ̊֐������Ȃ��Ƀ}�N���ɂ���Ƃ����Ȃ�܂��B

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

���̕t���Ă����Ƃ�����A�}�N���̈����ɂ��Ă��܂��B
����ł������̊֐����������Ă݂�ƁA

```clojure
(z-get-info port-name
            (z-send-command connection :version))  ;!!!
```

���[��B�����������B  
�҂đ҂āB  
z-send-command�ɂ́A�����Ƃ���open�̕Ԃ�l��connection���K�v�Ȃ̂ł��B
�Ƃ��낪�A���̒l�́A�}�N���̒��ɓ����Ă��܂��Ă���̂ŁA���Ƃ���͌���
�Ȃ��̂ł��B

���[��B  
�A�i�t�H���b�N�}�N���̏o�Ԃł��ˁB

���̑O��

#### �ϐ��̕ߑ�

lisp�ɂ̓}�N���̈����̕⑫�Ƃ�����肪�����āA��������Ȗ��Ȃ̂�
�����Aclojure�ł̓}�N�����ɕ��ʂ̃V���{���������Ȃ��悤�ɂ��Ă����
�h�~���Ă��܂��B

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

�����ŁA�}�N���̂Ȃ���let�ő��������V���{����x���g���Ă��܂��B���̃}
�N���͒�`�ł��܂����A�g�����Ƃ���Ɨ�O���������܂��B���̗�O�́A
qualified�̃V���{����let�Ɏg���Ȃ��Ƃ������̂ł����A 
�}�N����W�J���Ă݂�ƁAuser/x �̂悤�ɂȂ��Ă���̂��킩��܂��B

�}�N���̒���#��t���邱�ƂŁA�g����悤�ɂȂ�܂��B

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

������ނ���ߑ��ł���悤�ɂ���ɂ́A���������܂��B

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

�W�J�������̂�����Ƃ킩��Ƃ���Ax�����łłĂ��܂��B

���ꂪ���ɂȂ�̂͂��Ƃ��΂���ȏꍇ�ł��B

```clojure
(def x 10)

user> (yaa 10 #(+ % x))
(10 11 12 13 14 15 16 17 18 19)

user> (wao 10 #(+ % x))
ClassCastException clojure.lang.LazySeq cannot be cast to java.lang.Number  clojure.lang.Numbers.add (Numbers.java:126)
```

yaa�͎v�����Ƃ���̓�������Ă��܂����Awao�͗�O��f���Ă��܂��B���̂�
���A�O�Őݒ肵��x�ƃ}�N���̒���x���Փ˂��Ă��܂��Ă���̂ł��B

���̗�̂悤�ɗ\�������ɔ������Ă��܂��Ƃ�������ł͂���܂����A�v��I
�Ɏg���΁A�֗��Ɏg���܂��B

#### �A�i�t�H���b�N����

�}�N���̒��Œ�`����Ă���l���}�N���̊O����Q�Ƃł���悤�ɂ������̂��A
�A�i�t�H���b�N�}�N���ł��B (�`���b�g�Ⴄ����?)

�ڂ������Ƃ� On Lisp ��ǂ�ł��������B

�����قǂ̃}�N���Ɗ֐���������x�o���Ă����܂��B

```clojure
(defmacro send-and-get-01 [port-name & commands]
  '(let [connection# (z-open ~port-name)
        buff# (atom [])]
    (dorun (z-register-callback #(swap! buff# conj %))
           ~@commands
           (wait-for-data-end)
           (z-close connection#))
    (into [] @buff#)))

(z-get-info port-name
            (z-send-command connection :version))  ;!!!
```
�����ɂłĂ��� connection ���������̂������悤�ɂ������킯�ł��B  
���킩��ł��ˁB  
�������܂��B

```clojure
(defmacro send-and-get-02 [port-name & commands]
  '(let [~'a-con (z-open ~port-name)
        buff# (atom [])]
    (dorun (z-register-callback #(swap! buff# conj %))
           ~@commands
           (wait-for-data-end)
           (z-close ~'a-con))
    (into [] @buff#)))

(z-get-info port-name
            (z-send-command a-con :version))  ; OK
```

�����OK�B connection �Ƃ����͈̂�ʓI�����閼�O�Ȃ̂ŁA�A�i�t�H���ł�
�邱�Ƃ��킩��悤�� a-con �ɂ��Ă݂��B

����ł悵...�ł͂Ȃ��āA����ς�A�u�\�ł���΃}�N���͎g���ȁv�ł�
�邵�A�܂��āA�A�i�t�H���b�N�Ȃ��̂Ȃǂ�... �Ƃ������ƂŁA�ʉ����l����
�݂�B

### ����3 �A�i�t�H���b�N�łȂ��}�N��

���̗�ł́A�}�N�������ɂ݂̂��� connection ���g���ē��삷��֐����O��
��n���Ă�肽���킯�ł��B

����������̓}�N���ł��B �n�������̂́A���i�Ƃ��Ďg���܂��B

��������A���Ƃ��� connection ���֐��ɑ}�����Ă��܂��΂�������Ȃ���!

```clojure
(defmacro send-and-get [port-name & commands]
  '(let [connection# (z-open ~port-name)
        buff# (atom [])]
    (dorun (z-register-callback #(swap! buff# conj %))
           (-> connection#
               ~@commands)
           (wait-for-data-end)
           (z-close connection#))
    (into [] @buff#)))

(send-and-get port-name
            (z-send-command ,, :version))
```

�͂��B���̂悤�ɁA-> �}�N�����g���āA�n���ꂽ�֐�������1�Ԗڂ̈�����
connection�����ĕ]������悤�ɂ��Ă݂܂����B

���̕������ƁA�A�i�t�H���b�N�ł͂Ȃ��Ȃ�̂ł������肷��̂ł����A�t�ɁA
�n����֐������Ȃ炸1�Ԗڂ�connection���󂯎��悤�Ȃ��̂łȂ��Ă�

