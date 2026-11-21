package ca.gfh.birdtheatre;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Server-owned mail delivery; clients cannot control email content or recipients. */
@Service
class AccountEmail {
    private final ObjectProvider<JavaMailSender> sender;
    private final String from;

    AccountEmail(ObjectProvider<JavaMailSender> sender,
        @Value("${bird.mail.from:}") String from) {
        this.sender=sender;
        this.from=from==null ? "" : from.trim();
    }

    boolean configured() {
        return !from.isBlank() && sender.getIfAvailable()!=null;
    }

    void sendCode(String to, String purpose, String code) {
        String subject = "REGISTER".equals(purpose)
            ? "Bird Theatre · Verify your email" : "Bird Theatre · Reset your password";
        deliver(to,subject,"Your Bird Theatre code is: " + code + "\n\n"
            + "This code expires in 15 minutes. If you did not request it, ignore this message."
            + "\n\nBird Theatre");
    }

    void sendUsername(String to, String handle) {
        deliver(to,"Bird Theatre · Username reminder","Your Bird Theatre username is: " + handle
            + "\n\nIf you did not request this reminder, ignore this message.");
    }

    private void deliver(String to,String subject,String text) {
        if(!configured()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
            "Email delivery is not configured yet.");
        SimpleMailMessage mail=new SimpleMailMessage();
        mail.setFrom(from); mail.setTo(to); mail.setSubject(subject); mail.setText(text);
        try { sender.getObject().send(mail); }
        catch (org.springframework.mail.MailException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Email could not be delivered. Please try again later.");
        }
    }
}
