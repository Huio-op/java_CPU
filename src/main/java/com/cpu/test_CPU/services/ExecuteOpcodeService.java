package com.cpu.test_CPU.services;

import com.cpu.test_CPU.model.Opcodes;
import com.cpu.test_CPU.model.Registers;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;

@Service
public class ExecuteOpcodeService {

  public void execute(Opcodes op, ArrayList<String> args, StringBuilder response, String[][] memoryRef) {

    switch (op) {
      case NOOP: {
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "NOOP Opcode not implemented");
      }
      case MOV: {
        this.doMov(args.get(0), args.get(1));
        break;
      }
      case LOAD: {
        this.doLoad(args.get(0), args.get(1), memoryRef);
        break;
      }
      case SAVE: {
        this.doSave(args.get(0), args.get(1), memoryRef);
        break;
      }
      case INP: {
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "INP Opcode not implemented");
      }
      case OUT: {
        this.doOut(args.get(0), response);
        break;
      }
      case ADD: {

      }
      case SUB: {

      }
      case MUL: {

      }
      case DIV: {

      }
      case JGT: {

      }
      case JLT: {

      }
      case JGE: {

      }
      case JLE: {

      }
      case JEQ: {

      }
      case JMP: {

      }
      case CPY: {

      }
      case DEF: {

      }
      case HALT: {

      }
      default: {
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, op.name() + " Opcode not implemented");
      }
    }

  }

  public void doMov(String value, String registerCode) {
    final Registers register = this.getRegisterByCode(registerCode);

    register.getStack().push(value);
  }

  public void doLoad(String memoryAddress, String registerCode, String[][] memoryRef) {
    final Registers register = getRegisterByCode(registerCode);

    if (!memoryAddress.startsWith("@")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Memory addresses should start with '@'");
    }
    final String[] memoryAddresses = memoryAddress
      .replace("@", "")
      .split(",");

    final String memoryValue = memoryRef[Integer.parseInt(memoryAddresses[0])][Integer.parseInt(memoryAddresses[1])];
    register.getStack().push(memoryAddress);
  }

  public void doSave(String registerCode, String memoryAddress, String[][] memoryRef) {
    final Registers register = getRegisterByCode(registerCode);

    if (!memoryAddress.startsWith("@")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Memory addresses should start with '@'");
    }
    final String[] memoryAddresses = memoryAddress
      .replace("@", "")
      .split(",");

    memoryRef[Integer.parseInt(memoryAddresses[0])][Integer.parseInt(memoryAddresses[1])] = register.getStack().pop();
    ;
  }

  public void doOut(String var, StringBuilder response) {
    final Registers register = getRegisterByCode(var);
    response.append(register.getStack().pop());
    response.append("\n");
  }

  public Registers getRegisterByCode(String registerCode) {
    final Optional<Registers> registerOptional = Arrays.stream(Registers.values()).filter(reg -> reg.getHexCode().equals(registerCode)).findFirst();
    if (registerOptional.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Register not found");
    }
    return registerOptional.get();
  }

}
