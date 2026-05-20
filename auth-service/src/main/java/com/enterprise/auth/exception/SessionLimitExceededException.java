package com.enterprise.auth.exception;

public class SessionLimitExceededException extends AuthException {
    
    private final int currentSessions;
    private final int maxSessions;
    
    public SessionLimitExceededException(String userId, int currentSessions, int maxSessions) {
        super("SESSION_LIMIT_EXCEEDED", 
              String.format("Maximum session limit (%d) exceeded for user %s. Current sessions: %d", 
                          maxSessions, userId, currentSessions));
        this.currentSessions = currentSessions;
        this.maxSessions = maxSessions;
    }
    
    public int getCurrentSessions() {
        return currentSessions;
    }
    
    public int getMaxSessions() {
        return maxSessions;
    }
}
