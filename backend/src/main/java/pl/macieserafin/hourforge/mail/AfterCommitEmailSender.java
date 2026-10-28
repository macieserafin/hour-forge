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
    public void send(String to, String subject, String body) {
        afterCommit(() -> delegate.send(to, subject, body));
    }

    @Override
    public void sendWithAttachment(String to, String subject, String body, String filename, byte[] content, String contentType) {
        afterCommit(() -> delegate.sendWithAttachment(to, subject, body, filename, content, contentType));
    }

    private static void afterCommit(Runnable delivery) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            delivery.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                delivery.run();
            }
        });
    }

    @Override
    public void destroy() throws Exception {
        if (delegate instanceof DisposableBean disposable) disposable.destroy();
    }
}
