package com.cpu.test_CPU.model;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Optional;
import java.util.Stack;

public enum Registers {

  RA("0x00", new Stack<String>()),
  RB("0x01", new Stack<String>()),
  RC("0x02", new Stack<String>()),
  RD("0x03", new Stack<String>());

  private String hexCode;
  private Stack<String> stack;

  Registers(String hexCode, Stack<String> stack) {
    this.hexCode = hexCode;
    this.stack = stack;
  }

  public String getHexCode() {
    return hexCode;
  }

  public void setHexCode(String hexCode) {
    this.hexCode = hexCode;
  }

  public Stack<String> getStack() {
    return stack;
  }

  public void setStack(Stack<String> stack) {
    this.stack = stack;
  }

  public static Registers getRegisterByCode(String registerCode) {
    final Optional<Registers> registerOptional = Arrays.stream(Registers.values()).filter(reg -> reg.getHexCode().equals(registerCode)).findFirst();
    if (registerOptional.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Register not found");
    }
    return registerOptional.get();
  }
}
