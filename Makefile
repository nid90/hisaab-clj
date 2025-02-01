confgen:
	ln -s "$(CURDIR)/resources/hisaab.conf.toml" ~/hisaab.conf.toml

hdfc-cc:
	clj -M -m core cc ${FILE}

hdfc-bank:
	clj -M -m core bank ${FILE}

copy-toml:
	scp ~/hisaab.conf.toml ${DEST}:~
