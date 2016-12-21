# JDGバックアップリストアサンプル

## はじめに

本サンプルコードは、JDGのクライアントサーバモードで運用しているJDGクラスタのキャッシュをバックアップリストするための処理を実装したサンプルコードです。

現状、JDGのクライアントサーバモードでは、Hot Rodプロトコルメッセージのペイロードサイズが2GBに制約されており、ストリーム的にデータを抜き出すためのAPIが用意されていないため、以下の戦略によりJDGサーバのバックアップを取得します。

1. クライアントサーバモードでは、JDGサーバ側に開発者が実装したカスタムのリスナ、 フィルタ、コンバータ、ストア等をデプロイして、サーバ側の振舞いをカスタマ イズできることを利用し、上記カスタムモジュールのいずれかを使用して、CacheManagerおよび全てのCacheにアクセスして、iterativeにキャッシュエントリを読み出して、バックアップ処理するロジックをJDGサーバに組み込む方式を用いる。
2. ライブラリモードのCache.keySet()やentrySet()は、RemoteCacheと異なり、 該当のインスタンスが抱えるエントリだけを対象にするため、インスタンス間で 巨大なバルク転送をする必要がない。また、全てのインスタンスに対して同時並 列にバックアップ処理を開始すれば、サーバの数だけCPUとI/Oが広がるため、短時間にバックアップを行うことができる。
3. JDGサーバにデプロイした上記のカスタム処理（バックアップ）はHot Rod経由 では呼び出せないため、カスタムMBeanを登録して、バックアップ処理をキックするI/FをMBeanとして外部に公開する。従って、外部からはJMX経由でバックアップ処理を呼び出す形となる（メトリック取得の場合と同じ方式）
4. MBeanの登録・解除が適切に行われるようにするため、モジュールのライフサイクルが定義されているカスタムストアを利用して、バックアップロジックをMBeanとして登録するものとする。

## サンプルコードの動作確認

### 準備

サンプルコードはJavaで記述されており、プロジェクトのビルドツールにはMavenを使用しています。サンプルのツールを実行できるようにするためには、まずJDK 8とApache Mavenが利用可能である状態にしてください。以下のコマンドを実行し、

	$  mvn --version
    Apache Maven 3.3.1 (cab6659f9874fa96462afef40fcf6bc033d58c1c; 2015-03-14T05:10:27+09:00)
		:
	$ javac -version
    javac 1.8.0_65

### 設定ファイルの確認

本サンプルコードは、以下のプロパティ形式の設定ファイルを読み込んで使用します。

* backup.properties

それぞれの設定内容は以下の通りです。ご利用の環境に合わせて設定を変更してください。

src/main/resources/backup.properties:

    # Cache name list to backup.
    backup.cache_names = namedCache, \
        default
    
    # Base directory to store back files.
    backup.base_dir = /tmp/backup
    
    # Partition size: max number of entries to save in each backup file.
    backup.partition_size = 50000
    
    # Timeout(min) for backup process.
    backup.backup_timeout_min = 60
    
    # Timeout(min) for restore process.
    backup.restore_timeout_min = 60

バックアップデータは、backup.base_dir配下に、最大backup.partition\_sizeエントリ毎に切り取られたデータが以下のように保存されます。

    /tmp/backup/
    ├── backup-namedCache-localhost:11222-0.bin
    ├── backup-namedCache-localhost:11222-1.bin
    ├── backup-namedCache-localhost:11222-2.bin
    ├── backup-namedCache-localhost:11222-3.bin
    ├── backup-namedCache-localhost:11222-4.bin
    			:

### サンプルコードのビルド

サンプルコードは以下のコマンドを実行することでビルドが完了し、サンプルのツールを実行できる状態になります。

	$ cd jdg-backup-control
	$ mvn clean package

以下のような"BUILD SUCCESS"のメッセージが出力されたら、ビルドは成功です。

   		:
   	    [INFO] ------------------------------------------------------------------------
    [INFO] BUILD SUCCESS
    [INFO] ------------------------------------------------------------------------
    [INFO] Total time: 1.989 s
    [INFO] Finished at: 2015-11-27T15:57:18+09:00
    [INFO] Final Memory: 17M/220M
    [INFO] ------------------------------------------------------------------------

上記により、JDGサーバにデプロイ可能なバックアップ用モジュールがtargetディレクトリ配下に作成されます。

    $ ls target/
    ./                      generated-sources/      maven-status/
    ../                     jdg-backup-control.jar
    classes/                maven-archiver/
    
### モジュールのJDGサーバへのデプロイ

上記で出来上がったモジュールjdg-backup-control.jarをクラスタ内の全てのJDGインスタンスのdeploymentsディレクトリにコピーします。

    $ scp target/jdg-backup-control.jar \
        jboss@server1:/opt/jboss/jboss-datagrid-6.5.1-server/node1/deployments/
    $ scp target/jdg-backup-control.jar \
        jboss@server2:/opt/jboss/jboss-datagrid-6.5.1-server/node2/deployments/
        :

このモジュール(カスタムストア)を使用したダミーのキャッシュ(名前：cacheController)を作成し、JDGサーバを順にリスタートします。

キャッシュ作成とJDGサーバ再起動をスクリプトにしたファイルadd-controller-cache.cliを同梱していますので、以下のように実行してください。

    $ /opt/jboss/jboss-datagrid-6.5.1-server/bin/jboss-cli.sh \
    	--connect='remoting://<user>:<passwd>@server1:9999' \
    	--file=add-controller-cache.cli
    $ /opt/jboss/jboss-datagrid-6.5.1-server/bin/jboss-cli.sh \
    	--connect='remoting://<user>:<passwd>@server2:9999' \
    	--file=add-controller-cache.cli
    	:

--connectオプションの接続先URLの管理ポート番号はデフォルトが9999です。複数インスタンスを起動して、ポートオフセットを設定している場合は、そのオフセットの値を9999に加算した値を使用してください。

修正されたclustered.xmlファイルは以下の部分が追加されていることを確認してください。

                <distributed-cache name="cacheController" mode="SYNC" start="EAGER">
                    <store class="com.redhat.example.jdg.store.CacheControlStore"/>
                </distributed-cache>

### サンプルの実行

#### バックアップの実行

バックアップ、およびリストアは、JMXインタフェースを使用してキックします。上記モジュールをデプロイしたことにより、以下の名前のカスタムMBeanが登録されます。

    com.redhat.example:name=CacheController

JConsoleが接続できる場合は、JConsoleの左ペインで"com.redhat.example"を選択すると、CacheControllerという名前のMBeanが追加されていることが分かると思います。

バックアップを行う場合は、このMBeanの**backup()**オペレーションを実行します。

JMX接続する先のJDGインスタンスは、クラスタ内のどのインスタンスでも構いません。接続したJDGインスタンスを起点として、Distributed Executorが起動され、全てのJDGインスタンスで平行してバックアップ処理が開始されます。

バックアップの実行中は、CacheController MBeanの**BackupRunning**属性がtrueになります。全てのJDGサーバの全てのキャッシュのバックアップが完了すると、**BackupRunning**属性はfalseに戻ります。

なお、実行中または直前のバックアップの実行結果は、**BackupList**属性に出力されますので、合わせて確認ください。

#### リストアの実行

リストアは、バックアップと同様にCacheController MBeanの**restore()**オペレーションを呼び出すことで開始されます。

リストアの実行状態は**RestoreRunning**属性で、実行中または直前の実行結果は**RestoreList**に表示されます。

なお、バックアップおよびリストアのJMX APIを呼び出すためのshスクリプトも同梱していますので、合わせてご確認ください。

* bin/jdg-backup.sh	(バックアップ用)
* bin/jdg-restore.sh	(リストア用)

上記shスクリプトを使用する場合の接続情報は、bin/cachecontrol.jsに定義されています。使用する環境に応じて修正してください。

    // Any server to connect using JMX protocol.
    
    var server = "localhost:9999"
    
    // Authentication info: username and password.
    
    var username = "admin"
    var password = "welcome1!"


それぞれのshスクリプトは、引数なしで実行するだけです。バックアップを実行する時は、jdg-backup.shを実行します。

	$ bin/jdg-backup.sh

バックアップ用のディレクトリにバックアップファイルが保存されることを確認してください。

リストアする時は、jdg-restore.shを実行します。

	$ bin/jdg-restore.sh

## 補足事項

今回のサンプルを使用するにあたり、以下の点に注意してください。

* バックアップファイルは、JDGインスタンスが動作しているサーバ上のファイルシステムそれぞれに出力されます。従って、バックアップを取得した時のインスタンス構成より、onwers数以上に少ないインスタンス数を起動した状態でリストアを実行した場合、全てのデータがバックアップされません。各JDGインスタンスは自分のノード名が含まれるバックアップファイルしかリストアしませんので、もし、少ないインスタンスでリストアを行う場合は、バックアップファイルのファイル名のノード名部分を修正することで完全なリストアを行うことも可能です。

* サンプルコードのバックアップロジックは、複数エントリの一貫性を考慮していません。もし、特定のエントリ間の一貫性を確保した状態でバックアップを取得したい場合は、業務ロジックと同じ悲観ロックまたは楽観ロックを使用したデータの取得方法を使用するよう、BackupCommandクラスのcall()メソッドのロジックを修正する必要があります。リストア時の一貫性についても同様です。RestoreCommandクラスの記述内容を確認してください。

* バックアップおよびリストア時のJMX接続先のJDGインスタンスは任意のインスタンスで構いませんが、実行状態を表す属性はJDGインスタンス間で共有していません。backup()オペレーション、およびrestore()オペレーションを実行したら、同じJDGインスタンス上のMBeanで実行状態を確認してください。

* jdg-backup-controlモジュールのロジックを変更してJDGサーバに適用したい場合は、モジュールを再ビルド、再デプロイした後、cacheControllerキャッシュのみを再起動すれば変更したロジックをJDGサーバに反映することが出来ます。JDGサーバの再起動は必要ありませんし、ビジネスデータを含んだキャッシュのリバランスもトリガされません。cacheControllerキャッシュを再起動するスクリプト restart-controller-cache.cliが同梱されていますので、合わせてご確認ください。

# TODO

* Dist Exec先のCallableはタイムアウトしても走り続ける。タイムアウト値を引数で受け取り、呼び出し先でもループ内でチェックし、自律的に停止するようにする。

以上