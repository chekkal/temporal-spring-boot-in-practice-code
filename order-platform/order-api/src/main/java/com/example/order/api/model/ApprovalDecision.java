package com.example.order.api.model;

public class ApprovalDecision {
    private boolean approved;
    private String approver;
    private String comment;

    public ApprovalDecision() {}
    public ApprovalDecision(boolean approved, String approver, String comment) {
        this.approved = approved; this.approver = approver; this.comment = comment;
    }

    public boolean isApproved() { return approved; }
    public void setApproved(boolean approved) { this.approved = approved; }
    public String getApprover() { return approver; }
    public void setApprover(String approver) { this.approver = approver; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
