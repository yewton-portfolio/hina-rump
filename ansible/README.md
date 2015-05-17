ansible
=============

Prerequisites
-------------

### pyenv
- https://github.com/yyuu/pyenv#homebrew-on-mac-os-x

### Vagrant
- https://www.vagrantup.com/
- 1.6.0 以上
- 1.7.2 で動作確認済み

### (test)Ruby
- 2.0.0 以上
- 2.1.2p95 で動作確認済み

使い方
------

### common

```
pyenv install 2.7.8
easy_install pip
pip install -r requirements.txt
```

### local

```
vagrant up
```

### dev, staging, production, etc.

```
ansible-playbook -u root --ask-pass -i <INVENTORY_FILE> bootstrap.yml # 一番最初だけ
ansible-playbook -i <INVENTORY_FILE> site.yml
```

テスト
------

```
rbenv install 2.2.0
gem install bundler
bundle install --binstubs --path vendor/bundle
bin/kitchen test
```
