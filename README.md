# govpay-mod1-api
⚠️ Progetto deprecato. Il modello di pagamento 1 è dismesso da pagoPA e non è più supportato dal Nodo dei Pagamenti. Questo modulo non va adottato per nuove integrazioni devono usare il modello di pagamento corrente.

govpay-mod1-api fornisce le risorse per gestire il pagamento pagoPA secondo il modello 1, in cui il pagatore avvia la transazione direttamente dal portale dell'Ente Creditore. L'API consente a un portale di pagamento di avviare la transazione su una o più pendenze, di reindirizzare il pagatore verso il PSP e di consultare l'esito del pagamento.

Queste risorse facevano parte del progetto principale GovPay e ne sono state rimosse dopo la dismissione del modello 1 da parte di pagoPA. Il modulo le rende disponibili in forma separata per gli enti che, per casi particolari, devono ancora gestire flussi basati su questo modello, senza reintrodurre codice deprecato nel prodotto principale.

Ambito d'uso:
- installazioni esistenti che dipendono ancora da integrazioni di modello 1 e devono completarne la migrazione;
- gestione di pagamenti in corso o di casistiche residue legate al modello 1.

Compatibilità: il modulo richiede un'installazione GovPay compatibile e ne condivide la base dati. Verificare la matrice di compatibilità delle versioni prima dell'installazione.
