package ca.gfh.birdtheatre;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Same Mailjet provider as Electrical Workbench; HTTPS on Render Free,
 * with optional SMTP for local/paid environments.
 */
@Service
class AccountEmail {
    private final ObjectProvider<JavaMailSender> smtpSender;
    private final MailjetSendClient mailjet;
    private final String from;
    private final String apiKey;
    private final String secretKey;
    private final boolean smtpEnabled;

    AccountEmail(ObjectProvider<JavaMailSender> sender,
                 MailjetSendClient mailjet,
                 @Value("${bird.mail.from:}") String from,
                 @Value("${bird.mail.mailjet.api-key:}") String apiKey,
                 @Value("${bird.mail.mailjet.secret-key:}") String secretKey,
                 @Value("${bird.mail.smtp-enabled:false}") boolean smtpEnabled) {
        this.smtpSender=sender;
        this.mailjet=mailjet;
        this.from=from==null ? "" : from.trim();
        this.apiKey=apiKey==null ? "" : apiKey.trim();
        this.secretKey=secretKey==null ? "" : secretKey.trim();
        this.smtpEnabled=smtpEnabled;
    }

    boolean configured() {
        return !from.isBlank() && (mailjetReady() ||
            (smtpEnabled && smtpSender.getIfAvailable()!=null));
    }
    private boolean mailjetReady() {
        return !apiKey.isBlank() && !secretKey.isBlank();
    }

    void sendCode(String to,String purpose,String code) {
        String subject="REGISTER".equals(purpose)
            ? "Bird Theatre · Verify your email" : "Bird Theatre · Reset your password";
        deliver(to,subject,"Your Bird Theatre code is: "+code+"\n\n"
            +"This code expires in 15 minutes. If you did not request it, ignore this message."
            +"\n\nBird Theatre");
    }

    void sendUsername(String to,String handle) {
        deliver(to,"Bird Theatre · Username reminder",
            "Your Bird Theatre username is: "+handle
            +"\n\nIf you did not request this reminder, ignore this message.");
    }

    private void deliver(String to,String subject,String text) {
        if(!configured()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
            "Email delivery is not configured yet.");
        if(mailjetReady()) {
            mailjet.send(apiKey,secretKey,from,to,subject,text);
            return;
        }
        SimpleMailMessage mail=new SimpleMailMessage();
        mail.setFrom(from);mail.setTo(to);mail.setSubject(subject);mail.setText(text);
        try { smtpSender.getObject().send(mail); }
        catch(org.springframework.mail.MailException failure) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Email could not be delivered. Please try again later.");
        }
    }
}
