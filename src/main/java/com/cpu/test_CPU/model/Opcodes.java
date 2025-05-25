package com.cpu.test_CPU.model;

public enum Opcodes {

  NOOP("0x00", 0),
  MOV("0x01", 2),
  LOAD("0x02", 2),
  SAVE("0x03", 2),
  INP("0x04", 1),
  OUT("0x05", 1),
  HALT("0xFF", 0);

  private String hexCode;
  private int expectedArgs;

  Opcodes(String hex, int expectedArgs) {
    this.hexCode = hex;
    this.expectedArgs = expectedArgs;
  }

  public String getHexCode() {
    return hexCode;
  }

  public void setHexCode(String hexCode) {
    this.hexCode = hexCode;
  }

  public int getExpectedArgs() {
    return expectedArgs;
  }

  public void setExpectedArgs(int expectedArgs) {
    this.expectedArgs = expectedArgs;
  }
}
