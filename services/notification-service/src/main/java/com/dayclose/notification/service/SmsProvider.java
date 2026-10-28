package com.dayclose.notification.service;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * SMS provider using Twilio.
 * Falls back to mock mode if credentials not configured.
 */
@Component
public class SmsProvider {

    private static final Logger log = LoggerFactory.getLogger(SmsProvider.class);

    @Value("${dayclose.sms.twilio.account-sid:}")
    private String twilioAccountSid;

    @Value("${dayclose.sms.twilio.auth-token:}")
    private String twilioAuthToken;

    @Value("${dayclose.sms.twilio.from-number:}")
    private String twilioFromNumber;

    @Value("${dayclose.sms.twilio.whatsapp-from:}")
    private String twilioWhatsAppFrom;

    @Value("${dayclose.sms.enabled:false}")
    private boolean enabled;

    private boolean twilioConfigured = false;

    @PostConstruct
    public void init() {
        if (enabled && !twilioAccountSid.isEmpty() && !twilioAuthToken.isEmpty()) {
            try {
                Twilio.init(twilioAccountSid, twilioAuthToken);
                twilioConfigured = true;
                log.info("Twilio SMS provider initialized with account SID: {}***", 
                        twilioAccountSid.substring(0, Math.min(8, twilioAccountSid.length())));
            } catch (Exception e) {
                log.error("Failed to initialize Twilio: {}", e.getMessage());
                twilioConfigured = false;
            }
        } else {
            log.info("SMS provider running in MOCK mode (set dayclose.sms.enabled=true and configure Twilio credentials for real SMS)");
        }
    }

    /**
     * Send an SMS message via Twilio.
     * Falls back to mock logging if Twilio not configured.
     */
    public boolean send(String recipient, String message) {
        if (!twilioConfigured) {
            log.info("[MOCK SMS] To: {} | Message: {}", recipient, message);
            return true;
        }

        try {
            // Normalize phone number (Twilio requires E.164 format)
            String normalizedRecipient = normalizePhoneNumber(recipient);
            
            Message twilioMessage = Message.creator(
                    new PhoneNumber(normalizedRecipient),
                    new PhoneNumber(twilioFromNumber),
                    message
            ).create();

            log.info("SMS sent successfully via Twilio. SID: {} | To: {} | Status: {}", 
                    twilioMessage.getSid(), recipient, twilioMessage.getStatus());
            
            return true;
            
        } catch (Exception e) {
            log.error("Failed to send SMS to {}: {}", recipient, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Send a WhatsApp message via Twilio.
     * Falls back to mock logging if Twilio not configured.
     */
    public boolean sendWhatsApp(String recipient, String message) {
        if (!twilioConfigured) {
            log.info("[MOCK WHATSAPP] To: {} | Message: {}", recipient, message);
            return true;
        }

        try {
            // WhatsApp requires 'whatsapp:' prefix
            String normalizedRecipient = normalizePhoneNumber(recipient);
            String whatsappRecipient = "whatsapp:" + normalizedRecipient;
            
            Message twilioMessage = Message.creator(
                    new PhoneNumber(whatsappRecipient),
                    new PhoneNumber(twilioWhatsAppFrom),
                    message
            ).create();

            log.info("WhatsApp sent successfully via Twilio. SID: {} | To: {} | Status: {}", 
                    twilioMessage.getSid(), recipient, twilioMessage.getStatus());
            
            return true;
            
        } catch (Exception e) {
            log.error("Failed to send WhatsApp to {}: {}", recipient, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Normalize phone number to E.164 format.
     * Assumes Indian numbers (+91) if no country code provided.
     */
    private String normalizePhoneNumber(String phone) {
        if (phone == null || phone.isEmpty()) {
            throw new IllegalArgumentException("Phone number cannot be empty");
        }

        // Remove all non-digit characters
        String cleaned = phone.replaceAll("[^0-9]", "");

        // If starts with country code, use as-is
        if (cleaned.startsWith("91") && cleaned.length() == 12) {
            return "+" + cleaned;
        }

        // If 10 digits, assume Indian number
        if (cleaned.length() == 10) {
            return "+91" + cleaned;
        }

        // Otherwise return with + prefix
        if (!cleaned.startsWith("+")) {
            return "+" + cleaned;
        }

        return cleaned;
    }

    /**
     * Check if SMS provider is ready to send real messages.
     */
    public boolean isConfigured() {
        return twilioConfigured;
    }
}

