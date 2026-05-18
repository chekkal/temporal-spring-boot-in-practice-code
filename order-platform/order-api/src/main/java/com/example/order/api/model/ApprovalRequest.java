package com.example.order.api.model;

public class ApprovalRequest {
    private String approver;
    private String comment;

    public ApprovalRequest() {}

    public String getApprover() { return approver; }
    public void setApprover(String approver) { this.approver = approver; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
