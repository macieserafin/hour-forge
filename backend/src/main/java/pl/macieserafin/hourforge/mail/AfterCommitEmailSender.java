package pl.macieserafin.hourforge.mail;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Lets a message leave only when the transaction that caused it has been committed, so nobody is told about a change
 * that was rolled back. Outside a transaction the message is passed on at once.
 */
public class AfterCommitEmailSender implements EmailSender, DisposableBean {
    private final EmailSender delegate;

    public AfterCommitEmailSender(EmailSender delegate) {
        this.delegate = delegate;
    }

    @Override
    public void send(Email email) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            delegate.send(email);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                delegate.send(email);
            }
        });
    }

    @Override
    public void destroy() throws Exception {
        if (delegate instanceof DisposableBean disposable) disposable.destroy();
    }
}
