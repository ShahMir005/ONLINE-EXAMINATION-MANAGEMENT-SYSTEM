package edu.exampro.web.dto;

public class ChangePasswordForm {
    private Long userId;
    private String newPassword;
    private String confirmPassword;

    public ChangePasswordForm() {
    }

    public ChangePasswordForm(Long userId, String newPassword, String confirmPassword) {
        this.userId = userId;
        this.newPassword = newPassword;
        this.confirmPassword = confirmPassword;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
}
