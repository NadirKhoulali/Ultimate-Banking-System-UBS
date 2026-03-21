package net.austizz.ultimatebankingsystem.accountTypes;

public enum AccountTypes {
    CheckingAccount("Checking Account"),
    SavingAccount("Saving Account"),
    MoneyMarketAccount("Money Market Account"),
    CertificateAccount("Certificate of Deposit Account");

    public final String label;
    private AccountTypes(String label) {
        this.label = label;
    }
}
