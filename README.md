# hisaab-clj

हिसाब in Hindi means account.

This is a clj script to parse and create reports from my cc pdf statements. So far.

# How to use

Download delimited bank statements from hdfc and generate reports from them using `process` function in `statement` namspace.

```
;; example usage and output
(statement/process "<absolute-path-to-file>.txt")
{:totals
 {:withdrawls 125067.0,
  :deposits 6683.0,
  :expenditure 118384.0,
  :closing-balance 37404},
 :group-totals
 {:transport {:debit 1623, :credit 0},
  :f&b {:debit 60, :credit 0},
  :untagged {:debit 466848, :credit 590556},
  :donations {:debit 0, :credit 0},
  :groceries {:debit 5351, :credit 0},
  :subscriptions {:debit 0, :credit 21},
  :investments {:debit 611034, :credit 94073},
  :household {:debit 7971, :credit 0},
  :medicines {:debit 0, :credit 0},
  :shopping {:debit 5351, :credit 0}}}
;; => nil
```

For credit card statements, go to the `credit-card-statement` namespace and use the `process` fn.

```
;; example usage and output
(credit-card-statement/process "<absolute-path-to-file>.pdf")
{:total-credits "INR60,016.00",
 :total-debits "INR46,096.63",
 :debit-breakdown
 {:f&b "INR18,831.00",
  :untagged "INR538.63",
  :shopping "INR20,681.00",
  :groceries "INR397.00",
  :subscriptions "INR5,649.00"}}
;; => nil
```

CLI usage

```
clj -m core bank <abosulte-file-path>
```
or
```
clj -m core cc <abosulte-file-path>
```



# Future

- add reports for grouped and tagged expenditures
- add a sqlite database that can be locally stored
- add monthly comparisons for expenditures based on stored data
