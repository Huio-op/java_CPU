package com.cpu.test_CPU.model;

public enum Opcodes {

  NOOP("0x00", 0),
  MOV("0x01", 2),
  LOAD("0x02", 2),
  SAVE("0x03", 2),
  INP("0x04", 1),
  OUT("0x05", 1),
  ADD("0x06", 2),
  SUB("0x07", 2),
  MUL("0x08", 2),
  DIV("0x09", 2),
  JGT("0x0A", 3),
  JLT("0x0B", 3),
  JGE("0x0C", 3),
  JLE("0x0D", 3),
  JEQ("0x0E", 3),
  JMP("0x0F", 1),
  CPY("0x10", 2),
  DEF("0x11", 1),
  RET("0x12", 0),
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
