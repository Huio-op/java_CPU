package com.cpu.test_CPU.api;

import com.cpu.test_CPU.model.JumpPoint;
import com.cpu.test_CPU.model.Opcodes;
import com.cpu.test_CPU.model.Registers;
import com.cpu.test_CPU.services.ExecuteOpcodeService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/api/processor")
public class ProcessorResource {

  final String[][] memory = new String[16][16];
  final int romOffset = 8; // Primeiro oito linhas da memória é a ROM para guardar o código compilado
  // O resto da memória é o que pode ser utilizado pelo usuário
  String compiledCode = null;
  final Map<String, JumpPoint> jumpMap = new HashMap<>();

  private final ExecuteOpcodeService executeOpcodeService;

  public ProcessorResource(ExecuteOpcodeService executeOpcodeService) {
    this.executeOpcodeService = executeOpcodeService;
  }

  @PostMapping("/compile")
  public ProcessResponse compileCode(@RequestBody ProcessRequest request) {
    // Clear jump map
    this.jumpMap.clear();

    final String[] commandsByLine = request.sourceCode().split("\n");
    final StringBuilder compiledCode = new StringBuilder();
    compiledCode.append("-- BEGIN --\n");

    int memY = 0;
    int memX = 0;
    boolean insideDef = false;
    final ArrayList<JumpPlaceholder> jumpsDeclared = new ArrayList<>();

    for (String command : commandsByLine) {

      command = command.trim();
      if (command.isBlank()) {
        continue;
      }

      final String[] args = command.split(" ");
      final String func = args[0];
      final Opcodes opcode = Opcodes.valueOf(func);

      if (args.length - 1 != opcode.getExpectedArgs()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Wrong number of arguments, expected " + opcode.getExpectedArgs() + ", got " + args.length);
      }

      for (String arg : args) {
        if (memX == memory[memY].length) {
          memY++;
          memX = 0;
        }

        if (memY >= romOffset) {
          throw new ResponseStatusException(HttpStatus.INSUFFICIENT_STORAGE, "Ran out of ROM space");
        }

        final Optional<Opcodes> funcName = Arrays.stream(Opcodes.values()).filter(o -> o.name().equals(arg)).findFirst();
        if (funcName.isPresent()) {
          final Opcodes function = funcName.get();
          memory[memY][memX] = function.getHexCode();
          compiledCode.append("\n");
          compiledCode.append(function.getHexCode());

          if (function.equals(Opcodes.DEF)) {
            if (insideDef) {
              throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot define jump point inside a jump point");
            }

            insideDef = true;
          } else if (function.equals(Opcodes.RET)) {
            insideDef = false;
          }
        } else if (opcode.equals(Opcodes.DEF)) {

          String hexValue = this.getHexString(jumpMap.size());
          jumpMap.put(hexValue, new JumpPoint(memY, memX, arg));
          memory[memY][memX] = hexValue;
          compiledCode.append(" ").append(hexValue);

        } else if (arg.startsWith("R")) {

          final Optional<Registers> registerOptional = Arrays.stream(Registers.values()).filter(r -> r.name().equals(arg)).findFirst();
          if (registerOptional.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Register not found");
          }
          final Registers register = registerOptional.get();
          memory[memY][memX] = register.getHexCode();
          compiledCode.append(" ")
            .append(register.getHexCode());

        } else if (arg.startsWith("@")) {
          // TODO format memory address
          final String[] addresses = arg.replaceAll("@", "")
            .split(",");
          if (addresses.length > 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Memory addresses have only two coordinates");
          }

          final StringBuilder hexString = new StringBuilder()
            .append("0x");

          for (String s : addresses) {
            final int address = Integer.parseInt(s);
            if (address > 15) {
              throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The memory only has 15 addresses!");
            }
            hexString.append(Integer.toHexString(address));
          }

          memory[memY][memX] = hexString.toString();
          compiledCode.append(" ")
            .append(hexString.toString());

        } else if (this.isJumpOpcode(opcode)) {

          final String placeholderText = "${" + jumpsDeclared.size() + "}";
          final JumpPlaceholder placeholder = new JumpPlaceholder(memY, memX, placeholderText, arg);
          jumpsDeclared.add(placeholder);
          compiledCode.append(" ")
            .append(placeholderText);

        } else {
          String hexValue = this.getHexString(Integer.parseInt(arg));

          memory[memY][memX] = hexValue;
          compiledCode.append(" ")
            .append(hexValue);
        }
        memX++;
      }
    }
    if (insideDef) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "DEF not closed!");
    }

    for (JumpPlaceholder jumpPlaceholder : jumpsDeclared) {
      final Optional<Map.Entry<String, JumpPoint>> jumpPointOptional = jumpMap.entrySet().stream().filter((entry) ->
        entry.getValue().name().equals(jumpPlaceholder.originalName())
      ).findFirst();

      if (jumpPointOptional.isEmpty()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Jump point not defined!");
      }

      final String jumpPointKey = jumpPointOptional.get().getKey();
      memory[jumpPlaceholder.memY()][jumpPlaceholder.memX()] = jumpPointKey;
      final int indexToReplace = compiledCode.indexOf(jumpPlaceholder.placeholderTxt());
      compiledCode.replace(indexToReplace, indexToReplace + jumpPlaceholder.placeholderTxt().length(), jumpPointKey);
    }

    return new ProcessResponse(compiledCode.append("\n\n-- END --").toString(), memory);
  }

  @PostMapping("/execute")
  public ProcessResponse executeCode() {

    int i = 0;
    int j = 0;
    final StringBuilder response = new StringBuilder();

    while (true) {
      final String funcCode = memory[i][j];

      if (funcCode == null) {
        break;
      }

      final Optional<Opcodes> funcOptional = Arrays.stream(Opcodes.values()).filter(opcode ->
        opcode.getHexCode().equals(funcCode)
      ).findFirst();

      if (funcOptional.isEmpty()) {
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Function not found");
      }

      final Opcodes opcode = funcOptional.get();

      if (opcode.equals(Opcodes.HALT)) {
        break;
      } else {
        final ArrayList<String> args = new ArrayList<>();

        for (int k = 1; k <= opcode.getExpectedArgs(); k++) {
          if (j + k >= memory[i].length) {
            args.add(memory[i + 1][(j + k - (memory[i].length))]);
          } else {
            args.add(memory[i][j + k]);
          }
        }

        executeOpcodeService.execute(opcode, args, response, memory);

        if (j + opcode.getExpectedArgs() + 1 >= memory[i].length) {

          if (i + 1 >= memory.length) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "ROM memory Overflow");
          }

          i++;
          j = j + opcode.getExpectedArgs() - (memory[i].length - 1);
        } else {
          j += opcode.getExpectedArgs() + 1;
        }
      }
    }

    this.compiledCode = response.toString();
    return new ProcessResponse(this.compiledCode, memory);
  }

  @GetMapping("/state")
  public ProcessResponse getCurrentState() {
    return new ProcessResponse(this.compiledCode, memory);
  }

  public String buildMemoryString() {
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < memory.length; i++) {
      out.append("|");
      for (int j = 0; j < memory[i].length; j++) {
        out.append(memory[i][j] == null ? "0000" : memory[i][j]);
        out.append("|");
      }
      out.append("\n");
    }
    return out.toString();
  }

  private String getHexString(int integer) {
    String hexValue = Integer.toHexString(integer);
    if (hexValue.length() == 1) {
      hexValue = "0x0" + hexValue;
    } else {
      hexValue = "0x" + hexValue;
    }
    return hexValue;
  }

  private boolean isJumpOpcode(Opcodes opcode) {
    return switch (opcode) {
      case JEQ, JLE, JGT, JGE, JLT, JMP -> true;
      default -> false;
    };
  }

  public record ProcessRequest(String sourceCode) {
  }

  public record ProcessResponse(String data, String[][] memoryState) {
  }

  public record JumpPlaceholder(int memY, int memX, String placeholderTxt, String originalName) {
  }

}
