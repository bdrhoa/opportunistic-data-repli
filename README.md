## Automatic opportunistic data exchange over bluetooth on mobile devices

Only N900 and GNU/Linux systems are currently supported.

### Requirements

- couchdb (1.2.0 tested) - http://couchdb.apache.org/
- blueproxy (1.3 tested) - http://anil.recoil.org/projects/blueproxy.html
- socat
- curl

### Use

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

- install blueproxy from source

        $ sudo apt-get install libbluetooth-dev
        $ wget http://anil.recoil.org/projects/blueproxy-1.3.tar.gz
        $ tar xzvf blueproxy-*.tar.gz
        $ cd blueproxy-*
        $ configure --prefix=$BLUEPROXYDIR
        $ make
        $ make install

- run couchdb built from source

        $ screen # (or terminal)
        $ cd $COUCHDBDIR
        $ bin/couchdb

- install bt-opp-data-exch

        $ git clone https://github.com/crackleware/bt-opp-data-exch

- (optional) in couchdb admin interface, create database
  'phy-f2f-acc-db'; database name is configurable in couchdb-repli-run

- run bt-opp-data-exch

        $ screen # (or terminal)
        $ cd bt-opp-data-exch
        $ PATH=$BLUEPROXYDIR/bin:$PATH ./couchdb-repli-run

        $ # in regular x-terminal (not in debian chroot):
        $ PATH=$BLUEPROXYDIR/bin:$PATH blueproxy -p 5984

#### Setup on GNU/Linux:

- very similar to N900 but running ./couchdb-repli-run is enough

### Todo

- security

- Android, iOS

### Related

- https://code.google.com/p/haggle/ - Haggle - A content-centric
  network architecture for opportunistic communication

- http://ica1www.epfl.ch/haggle/ - Haggle - A European Union funded
  project in Situated and Autonomic Communications

### Contact

predrg@gmail.com
