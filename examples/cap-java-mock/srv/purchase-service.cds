@path: 'purchase-lab'
@requires: 'authenticated-user'
@title: 'LOCAL_CAP_MOCK_ADAPTER'
service PurchaseLab {
  type ApprovalCommand {
    @mandatory
    @assert.format: '^[A-Za-z0-9_-]{1,64}$'
    eventId        : String(64);
    @mandatory
    @assert.format: '^[A-Za-z0-9_-]{1,64}$'
    requestId      : String(64);
    @mandatory
    @assert.format: '^(0|[1-9][0-9]{0,18})$'
    requestVersion : String(19);
    @mandatory
    @assert.format: '^(0|[1-9][0-9]{0,18})$'
    budgetVersion  : String(19);
  }

  type Receipt {
    eventId         : String;
    requestId       : String;
    tenant          : String;
    actor           : String;
    outcome         : String;
    reason          : String;
    before          : String;
    after           : String;
    requestVersion  : String(19);
    budgetVersion   : String(19);
    budgetMinor     : Integer64;
    reservedMinor   : Integer64;
    compilerVersion : String;
    sourceSha256    : String;
  }

  type ApprovalResult {
    profile : String;
    status  : String;
    message : String;
    receipt : Receipt;
  }

  type Snapshot {
    profile        : String;
    tenant         : String;
    requestId      : String;
    state          : String;
    requestVersion : String(19);
    budgetVersion  : String(19);
    budgetMinor    : Integer64;
    currency       : String;
  }

  @requires: 'Approver'
  action approve(@mandatory command: ApprovalCommand) returns ApprovalResult;

  @requires: 'Approver'
  function snapshot(@mandatory requestId: String(64)) returns Snapshot;
}
