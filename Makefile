confgen:
	clj -M -m core confgen

hdfc-cc:
	clj -M -m core cc ${FILE}

hdfc-bank:
	clj -M -m core bank ${FILE}
