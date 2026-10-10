package org.acme.users;

public enum AccountLevel {
  BASIC(1),
  PREMIUM(2),
  ADMIN(null);

  private final Integer ninjaLimit;

  AccountLevel(Integer ninjaLimit) {
    this.ninjaLimit = ninjaLimit;
  }

  public Integer getNinjaLimit() {
    return ninjaLimit;
  }
}
