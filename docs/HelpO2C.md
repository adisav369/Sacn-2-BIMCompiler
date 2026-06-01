# Order to Cash — ReadMe (Help steps)

> Step-by-step help for the Order-to-Cash flow. The **numbered bubble** beside each step is the same one you see on the [Glassbowl explorer](glassbowl.html): tick **NeedHelp?**, click a numbered `?` bubble, then **ShowMe** to be taken to it. One real order — Sales Order **#80001** ($100.70) — traced end to end from the actual data.


<a id="o2c"></a>

## <span style="display:inline-block;min-width:1.55em;height:1.55em;line-height:1.55em;text-align:center;border-radius:50%;background:#1668a8;color:#fff;font-size:.72em;font-weight:700;margin-right:.45em;vertical-align:middle;border:1px solid #7fd6e0">▶</span> Order to Cash

![Figure 1.0 — the whole lit O2C chain on the map](figs/o2c_0_o2c.png)

*Figure 1.0 — the whole lit O2C chain on the map*


<p>The most common journey in any business: a customer <b>orders</b>, you <b>ship</b>, you <b>invoice</b>, they <b>pay</b>, and the payment is <b>reconciled</b> to the invoice. Below, one real order &mdash; Sales Order <b>#80001</b> ($100.70) &mdash; is traced end to end from the actual data.</p>


[↑ back to the flow](#order-to-cash-readme-help-steps)


<a id="c_order"></a>

## <span style="display:inline-block;min-width:1.55em;height:1.55em;line-height:1.55em;text-align:center;border-radius:50%;background:#1668a8;color:#fff;font-size:.72em;font-weight:700;margin-right:.45em;vertical-align:middle;border:1px solid #7fd6e0">1</span> Order

![Figure 1.1 — the Order bubble focused, Data tab showing #80001](figs/o2c_1_c_order.png)

*Figure 1.1 — the Order bubble focused, Data tab showing #80001*


<p>The <b>Order</b> records what the customer committed to buy. Here it is Sales Order <b>#80001</b>, total <b>$100.70</b>. Everything downstream &mdash; shipment, invoice, payment &mdash; references this one document. It needs no server: the order is a single signed entry appended at the edge.</p>


[↑ back to the flow](#order-to-cash-readme-help-steps)


<a id="m_inout"></a>

## <span style="display:inline-block;min-width:1.55em;height:1.55em;line-height:1.55em;text-align:center;border-radius:50%;background:#1668a8;color:#fff;font-size:.72em;font-weight:700;margin-right:.45em;vertical-align:middle;border:1px solid #7fd6e0">2</span> Shipment

![Figure 1.2 — the Shipment bubble focused, Data tab](figs/o2c_2_m_inout.png)

*Figure 1.2 — the Shipment bubble focused, Data tab*


<p>The <b>Shipment</b> (Material In/Out) is the goods leaving the warehouse against the order. It points back at the order it fulfils, so the link lives in the data &mdash; not in a session.</p>


[↑ back to the flow](#order-to-cash-readme-help-steps)


<a id="c_invoice"></a>

## <span style="display:inline-block;min-width:1.55em;height:1.55em;line-height:1.55em;text-align:center;border-radius:50%;background:#1668a8;color:#fff;font-size:.72em;font-weight:700;margin-right:.45em;vertical-align:middle;border:1px solid #7fd6e0">3</span> Invoice

![Figure 1.3 — the Invoice bubble focused, Data tab showing $100.70](figs/o2c_3_c_invoice.png)

*Figure 1.3 — the Invoice bubble focused, Data tab showing $100.70*


<p>The <b>Invoice</b> bills the customer &mdash; <b>$100.70</b>, the same figure as the order: what was ordered, shipped and billed all agree. The amount is <i>derived</i> by replaying the log, not stored twice.</p>


[↑ back to the flow](#order-to-cash-readme-help-steps)


<a id="c_payment"></a>

## <span style="display:inline-block;min-width:1.55em;height:1.55em;line-height:1.55em;text-align:center;border-radius:50%;background:#1668a8;color:#fff;font-size:.72em;font-weight:700;margin-right:.45em;vertical-align:middle;border:1px solid #7fd6e0">4</span> Payment

![Figure 1.4 — the Payment bubble focused, Data tab](figs/o2c_4_c_payment.png)

*Figure 1.4 — the Payment bubble focused, Data tab*


<p>The <b>Payment</b> is the customer settling up. It is recorded as its own document, ready to be matched against the invoice.</p>


[↑ back to the flow](#order-to-cash-readme-help-steps)


<a id="c_allocationline"></a>

## <span style="display:inline-block;min-width:1.55em;height:1.55em;line-height:1.55em;text-align:center;border-radius:50%;background:#1668a8;color:#fff;font-size:.72em;font-weight:700;margin-right:.45em;vertical-align:middle;border:1px solid #7fd6e0">5</span> Reconciled (Allocation)

![Figure 1.5 — the Allocation bubble focused, Data tab showing $98.50](figs/o2c_5_c_allocationline.png)

*Figure 1.5 — the Allocation bubble focused, Data tab showing $98.50*


<p>The <b>Allocation</b> reconciles the payment to the invoice &mdash; here <b>$98.50</b>. This is the careful step a bookkeeper double-checks, and it is the <i>one matcher</i> that folds receivable against receipt for every kind of match.</p>


[↑ back to the flow](#order-to-cash-readme-help-steps)
