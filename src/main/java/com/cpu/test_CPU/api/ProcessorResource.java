package com.cpu.test_CPU.api;

import com.cpu.test_CPU.model.JumpPoint;
import com.cpu.test_CPU.model.Opcodes;
import com.cpu.test_CPU.model.Registers;
import com.cpu.test_CPU.services.ExecuteOpcodeService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import static com.cpu.test_CPU.model.Registers.getRegisterByCode;

@RestController
@RequestMapping("/api/processor")
public class ProcessorResource {

  final String[][] memory = new String[16][16];
  final int romOffset = 8; // Primeiro oito linhas da memória é a ROM para guardar o código compilado
  // O resto da memória é o que pode ser utilizado pelo usuário
  String compiledCode = null;
  final Map<String, JumpPoint> jumpMap = new HashMap<>();

  final AtomicInteger executionMemY = new AtomicInteger(0);
  final AtomicInteger executionMemX = new AtomicInteger(0);
  StringBuilder executionResponse = new StringBuilder();

  private final ExecuteOpcodeService executeOpcodeService;

  public ProcessorResource(ExecuteOpcodeService executeOpcodeService) {
    this.executeOpcodeService = executeOpcodeService;
  }

  @PostMapping("/compile")
  public ProcessResponse compileCode(@RequestBody ProcessRequest request) {
    // Clear jump map
    this.jumpMap.clear();
    this.clearExecutionContext();

    final String[] commandsByLine = request.sourceCode().split("\n");
    final StringBuilder compiledCode = new StringBuilder();
    compiledCode.append("-- BEGIN --\n");

    int memY = 0;
    int memX = 0;
    boolean insideDef = false;
    final ArrayList<JumpPlaceholder> jumpsDeclared = new ArrayList<>();

    clearRegisterStacks();

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

          validateRamMemoryAddress(args, function);

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

    return new ProcessResponse(compiledCode.append("\n\n-- END --").toString(), memory, getRegistersMap(), false);
  }

  private static void validateRamMemoryAddress(String[] args, Opcodes function) {
    if (!(function.equals(Opcodes.SAVE) || function.equals(Opcodes.LOAD))) {
      return;
    }

    String memory = "";

    if (function.equals(Opcodes.SAVE)) {
      memory = args[2];
    } else {
      memory = args[1];
    }

    if (!memory.startsWith("@")) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Memory address must start with @");
    }

    final String[] addresses = memory.replaceAll("@", "")
            .split(",");

    if (addresses.length > 2) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Memory addresses have only two coordinates");
    }

    if (Integer.parseInt(addresses[0]) < 9 || Integer.parseInt(addresses[0]) > 15) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'@"+addresses[0]+","+addresses[1]+"' is out of RAM's bounds.\n\n RAM memory starts at @9,0 and ends at @15,15!");
    }

    if (Integer.parseInt(addresses[1]) < 0 || Integer.parseInt(addresses[1]) > 15) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The memory only has 15 addresses!");
    }
  }

  @PostMapping("/execute")
  public ProcessResponse executeCode(boolean continueExecutionContext, String input) {

    if (!continueExecutionContext) {
      this.clearExecutionContext();
    }

    final AtomicReference<JumpPoint> currentReturnPoint = new AtomicReference<>();

    final BiFunction<Integer, Integer, Void> executeJump = (newY, newX) -> {
      if (currentReturnPoint.get() != null && newY.equals(currentReturnPoint.get().rowOrigin()) && newX.equals(currentReturnPoint.get().colOrigin())) {
        currentReturnPoint.set(null);
      } else {
        currentReturnPoint.set(new JumpPoint(executionMemY.get(), executionMemX.get(), "RET"));
      }
      executionMemY.set(newY);
      executionMemX.set(newX);
      return null;
    };

    while (true) {

      final Opcodes opcode = this.getOpcodesInCursor(executionMemY, executionMemX);
      if (opcode == null) {
        break;
      }

      if (opcode.equals(Opcodes.HALT)) {
        break;
      }

      if ((opcode.equals(Opcodes.INP) || opcode.equals(Opcodes.INP_C)) && !continueExecutionContext) {
        return new ProcessResponse(null, memory, getRegistersMap(), true);
      }

      final ArrayList<String> args = new ArrayList<>();

      for (int k = 1; k <= opcode.getExpectedArgs(); k++) {
        if (executionMemX.get() + k >= memory[executionMemY.get()].length) {
          args.add(memory[executionMemY.get() + 1][(executionMemX.get() + k - (memory[executionMemY.get()].length))]);
        } else {
          args.add(memory[executionMemY.get()][executionMemX.get() + k]);
        }
      }

      final boolean executionResult = executeOpcodeService.execute(opcode, args, executionResponse, memory, jumpMap, executeJump, currentReturnPoint.get(), input);

      continueExecutionContext = false;

      int argsOffset = 0;
      if (opcode.equals(Opcodes.RET) || isJumpOpcode(opcode)) {
        final Opcodes jumpOpcode = this.getOpcodesInCursor(executionMemY, executionMemX);
        if (jumpOpcode == null) {
          throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Original jump function not found");
        }
        argsOffset += !isJumpOpcode(opcode) || !executionResult ? jumpOpcode.getExpectedArgs() : 0;
      } else {
        argsOffset = opcode.getExpectedArgs();
      }

      if (executionMemX.get() + argsOffset + 1 >= memory[executionMemY.get()].length) {

        if (executionMemY.get() + 1 >= memory.length) {
          throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "ROM memory Overflow");
        }

        executionMemY.set(executionMemY.get() + 1);
        executionMemX.set(executionMemX.get() + argsOffset - (memory[executionMemY.get()].length - 1));
      } else {
        executionMemX.set(executionMemX.get() + argsOffset + 1);
      }
    }

    this.compiledCode = executionResponse.toString();
    return new ProcessResponse(this.compiledCode, memory, getRegistersMap(), false);
  }

  @GetMapping("/state")
  public ProcessResponse getCurrentState() {
    return new ProcessResponse(this.compiledCode, memory, getRegistersMap(), false);
  }

  @PostMapping("/input")
  public ProcessResponse processInput(@RequestBody ProcessRequest request) {
    String input = request.sourceCode().trim();
    return this.executeCode(true, input);
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

  private Opcodes getOpcodesInCursor(AtomicInteger memY, AtomicInteger memX) {
    final String funcCode = memory[memY.get()][memX.get()];

    if (funcCode == null) {
      return null;
    }

    final Optional<Opcodes> funcOptional = Arrays.stream(Opcodes.values()).filter(opcode ->
      opcode.getHexCode().equals(funcCode)
    ).findFirst();

    if (funcOptional.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Function not found");
    }

    return funcOptional.get();
  }

  private List<String> stackToList(Stack<String> stack) {
    if (stack == null || stack.isEmpty()) {
      return new ArrayList<>();
    }

    List<String> list = new ArrayList<>();
    Object[] stackArray = stack.toArray();

    for (int i = stackArray.length - 1; i >= 0; i--) {
      list.add((String) stackArray[i]);
    }

    return list;
  }

  private void clearExecutionContext() {
    executionMemY.set(0);
    executionMemX.set(0);
    clearRegisterStacks();
    executionResponse = new StringBuilder();
  }

  private Map<String, List<String>> getRegistersMap() {
    Map<String, List<String>> registers = new HashMap<>();

    registers.put("RA", stackToList(getRegisterByCode(Registers.RA.getHexCode()).getStack()));
    registers.put("RB", stackToList(getRegisterByCode(Registers.RB.getHexCode()).getStack()));
    registers.put("RC", stackToList(getRegisterByCode(Registers.RC.getHexCode()).getStack()));
    registers.put("RD", stackToList(getRegisterByCode(Registers.RD.getHexCode()).getStack()));

    return registers;
  }

  private void clearRegisterStacks(){
    getRegisterByCode(Registers.RA.getHexCode()).getStack().clear();
    getRegisterByCode(Registers.RB.getHexCode()).getStack().clear();
    getRegisterByCode(Registers.RC.getHexCode()).getStack().clear();
    getRegisterByCode(Registers.RD.getHexCode()).getStack().clear();
  }
  public record ProcessRequest(String sourceCode) {
  }

  public record ProcessResponse(String data, String[][] memoryState, Map<String, List<String>> registers, boolean needsInput) {
  }

  public record JumpPlaceholder(int memY, int memX, String placeholderTxt, String originalName) {
  }

}
