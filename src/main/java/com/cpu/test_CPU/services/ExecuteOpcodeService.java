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

  public void execute(Opcodes op, ArrayList<String> args, StringBuilder response) {

    switch (op) {
      case NOOP: {
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Opcode not implemented");
      }
      case MOV: {
        this.doMov(args.get(0), args.get(1));
        break;
      }
      case LOAD:
      case SAVE:
      case INP: {
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Opcode not implemented");
      }
      case OUT: {
        this.doOut(args.get(0), response);
        break;
      }
      default: {
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Opcode not implemented");
      }
    }

  }

  public void doMov(String value , String registerCode) {
    final Registers register = this.getRegisterByCode(registerCode);

    register.getStack().push(value);
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
