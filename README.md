## Automatic opportunistic data replication using CouchDB (over ethernet, WiFi and bluetooth)

**STATUS: PROOF OF CONCEPT IMPLEMENTATION**

All devices have local CouchDB instance. This allows automatic
propagation of data and couchapps operating on that data.

Auto sync modes of operation:

- with (W)LAN peers discovered by network broadcast

- with known bluetooth devices (by bluetooth address)

- with discoverable bluetooth devices [work in progress]

Tested on: Android 2.3.5, N900, ArchLinux.

#### Setup on Android:

- install and start MobileFuton -
  https://play.google.com/store/apps/details?id=com.mainerror.mobilefuton

- install and start precompiled Auto Opportunistic CouchDB Replicator
  - https://github.com/crackleware/opportunistic-data-repli/blob/master/and-opp-couchdb-repli/bin/andoppcouchdbrepli-debug.apk

- OR build it from source

        $ cd and-opp-couchdb-repli
        $ ant debug # .apk is created in bin subdir

##### Configuration

For bluetooth known-nodes mode, create
/mnt/sdcard/couchdb-repli-known_nodes.txt with following format:

        HH:HH:HH:HH:HH:HH optional-device-description
        ...

HH:HH:HH:HH:HH:HH is bluetooth device address.

#### Requirements on N900 and GNU/Linux

  - couchdb (1.2.0 tested) - http://couchdb.apache.org/
  - blueproxy (1.3 tested) - http://anil.recoil.org/projects/blueproxy.html
  - socat
  - curl

#### Setup on N900:

- install easy-deb-chroot, enter chroot

        $ debbie

- install couchdb

        $ # with apt-get
        $ sudo apt-get install couchdb

        $ # OR from source (if very old version is in apt-get repository)
        $ sudo apt-get install erlang erlang-os-mon erlang-eunit
        $ COUCHDBDIR=`pwd`/couchdb-inst
        $ wget http://www.us.apache.org/dist/couchdb/releases/1.2.0/apache-couchdb-1.2.0.tar.gz
        $ tar xzvf apache-couchdb-*.tar.gz
        $ cd apache-couchdb-*
        $ configure --prefix=$COUCHDBDIR
        $ make
        $ make install
        
        $ # edit $COUCHDBDIR/etc/local.ini, in [httpd], bind_address = 0.0.0.0

- install blueproxy from patched source

        $ sudo apt-get install libbluetooth-dev
        $ wget http://anil.recoil.org/projects/blueproxy-1.3.tar.gz
        $ tar xzvf blueproxy-*.tar.gz
        $ cd blueproxy-*
        $ patch -p1 < blueproxy-hardcoded-service-uuid-and-max-channels-limit.patch
        $ configure --prefix=$BLUEPROXYDIR
        $ make
        $ make install

- run couchdb built from source

        $ screen # (or terminal)
        $ cd $COUCHDBDIR
        $ bin/couchdb

- install opportunistic-data-repli

        $ git clone https://github.com/crackleware/opportunistic-data-repli

- (optional) in couchdb admin interface, create database
  'phy-f2f-acc-db'; database name is configurable in couchdb-repli-run

- run opportunistic-data-repli

        $ screen # (or terminal)
        $ cd opportunistic-data-repli
        $ PATH=$BLUEPROXYDIR/bin:$PATH ./couchdb-repli-run

        $ # in regular x-terminal (not in debian chroot):
        $ PATH=$BLUEPROXYDIR/bin:$PATH blueproxy -p 5984

#### Setup on GNU/Linux:

- very similar to N900 but running couchdb-repli-run is enough

### Use

Change data in DB on any device (with usual tools or through
couchapps) and changes will be propagated to other devices eventually.

### Todo

- couchapp examples

- native Android apps:

  - physical friend-to-friend networking (for info that you would
    otherwise tell to people you meet but usually forget - no need for
    reminders - just declare to share it eventually)

  - auto insert captured semantically-(geo/face/object)-tagged media
    (like images, video or sound recordings) into local couchdb for
    later dissemination; subscribe to Android media creation
    notifications

  - knowledge and semantic web data acquisition and dissemination
    (probably additionally externally indexed to enable efficient
    processing)

- partial replication (according to filters defined by user)

- explicit replication initiation to support live local user couchapps

- create patch for blueproxy to support configurable (by command-line
  option) service class UUID

- better local tcp port allocation

- do refactoring, cleanup codebase

- Android app:
  - do all networking in Android service
  - UI for adding known (of friends) bluetooth addresses
  - configuration screen
  - (maybe) embed Mobile Couchbase - https://github.com/couchbase/Android-Couchbase

- tests

- distribute Auto Opportunistic CouchDB Replicator and other
  native Android apps through play.google.com

- security, encryption...

- iOS app (use TouchDB)

### Related

- https://code.google.com/p/haggle/ - Haggle - A content-centric
  network architecture for opportunistic communication

- http://ica1www.epfl.ch/haggle/ - Haggle - A European Union funded
  project in Situated and Autonomic Communications

- http://www.foo.be/forban/ - Forban - a simple link-local
  opportunistic p2p free software (or how to share files with your
  local neighbors)

### License

WTFPL

### Contact

predrg@gmail.com
