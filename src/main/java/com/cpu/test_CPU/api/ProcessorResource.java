package com.cpu.test_CPU.api;

import com.cpu.test_CPU.model.Flags;
import com.cpu.test_CPU.model.JumpPoint;
import com.cpu.test_CPU.model.Opcodes;
import com.cpu.test_CPU.model.Registers;
import com.cpu.test_CPU.services.ExecuteOpcodeService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

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
  final Stack<JumpPoint> returnPointStack = new Stack<>();
  StringBuilder executionResponse = new StringBuilder();
  Flags currentFlag = null;

  private final ExecuteOpcodeService executeOpcodeService;

  public ProcessorResource(ExecuteOpcodeService executeOpcodeService) {
    this.executeOpcodeService = executeOpcodeService;
  }

  @PostMapping("/compile")
  public ProcessResponse compileCode(@RequestBody ProcessReq request) {
    // Clear jump map
    this.jumpMap.clear();
    this.clearExecutionContext(false);

    final String[] commandsByLine = request.sourceCode().split("\n");

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

          if (function.equals(Opcodes.DEF)) {
            if (insideDef) {
              throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot define jump point inside a jump point");
            }

            insideDef = true;
          } else if (function.equals(Opcodes.RET)) {
            insideDef = false;
          } else if (function.equals(Opcodes.HALT) && insideDef) {
            insideDef = false;
          }

          validateRamMemoryAddress(args, function);

        } else if (opcode.equals(Opcodes.DEF)) {

          String hexValue = this.getHexString(jumpMap.size());
          jumpMap.put(hexValue, new JumpPoint(memY, memX, arg, false));
          memory[memY][memX] = hexValue;

        } else if (arg.startsWith("R")) {

          final Optional<Registers> registerOptional = Arrays.stream(Registers.values()).filter(r -> r.name().equals(arg)).findFirst();
          if (registerOptional.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Register not found");
          }
          final Registers register = registerOptional.get();
          memory[memY][memX] = register.getHexCode();

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

        } else if (this.isJumpOpcode(opcode)) {

          final String placeholderText = "${" + jumpsDeclared.size() + "}";
          final JumpPlaceholder placeholder = new JumpPlaceholder(memY, memX, placeholderText, arg);
          jumpsDeclared.add(placeholder);
        } else {
          String hexValue = this.getHexString(Integer.parseInt(arg));

          memory[memY][memX] = hexValue;
        }
        memX++;
      }
    }

    for (JumpPlaceholder jumpPlaceholder : jumpsDeclared) {
      final Optional<Map.Entry<String, JumpPoint>> jumpPointOptional = jumpMap.entrySet().stream().filter((entry) ->
        entry.getValue().name().equals(jumpPlaceholder.originalName())
      ).findFirst();

      if (jumpPointOptional.isEmpty()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Jump point not defined: " + jumpPlaceholder.originalName());
      }

      final String jumpPointKey = jumpPointOptional.get().getKey();
      memory[jumpPlaceholder.memY()][jumpPlaceholder.memX()] = jumpPointKey;
    }

    this.compiledCode = this.buildCompiledCode(false);
    return new ProcessResponse(this.compiledCode, memory, getRegistersMap());
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
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'@" + addresses[0] + "," + addresses[1] + "' is out of RAM's bounds.\n\n RAM memory starts at @9,0 and ends at @15,15!");
    }

    if (Integer.parseInt(addresses[1]) < 0 || Integer.parseInt(addresses[1]) > 15) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The memory only has 15 addresses!");
    }
  }

  @PostMapping("/execute")
  public ExecuteResponse executeCode(@RequestBody ExecuteReq req, boolean continueExecutionContext) throws InterruptedException {

    if ((!continueExecutionContext && !req.step()) || (this.currentFlag != null && this.currentFlag.equals(Flags.ENDED))) {
      this.clearExecutionContext(true);
    }

    final String compiledCode = req.step() ? buildCompiledCode(true) : this.compiledCode;
    final int executedY = this.executionMemY.get();
    final int executedX = this.executionMemX.get();

    final Function<JumpPoint, Void> executeJump = (jumpPoint) -> {
      if (!jumpPoint.isReturnPoint()) {
        returnPointStack.push(new JumpPoint(executionMemY.get(), executionMemX.get(), "RET", true));
      }
      executionMemY.set(jumpPoint.rowOrigin());
      executionMemX.set(jumpPoint.colOrigin());
      return null;
    };

    while (true) {

      final Opcodes opcode = this.getOpcodesInCursor(executionMemY.get(), executionMemX.get());
      if (opcode == null || opcode.equals(Opcodes.HALT)) {
        currentFlag = Flags.ENDED;
        break;
      }

      if ((opcode.equals(Opcodes.INP) || opcode.equals(Opcodes.INP_C)) && !continueExecutionContext) {
        switch (opcode) {
          case INP -> this.currentFlag = Flags.NEEDS_INPUT_I;
          case INP_C -> this.currentFlag = Flags.NEEDS_INPUT_C;
        }
        return new ExecuteResponse(null, memory, getRegistersMap(),
          this.compiledCode, this.executionMemY.get(), this.executionMemX.get(), this.currentFlag);
      }

      final ArrayList<String> args = new ArrayList<>();

      for (int k = 1; k <= opcode.getExpectedArgs(); k++) {
        if (executionMemX.get() + k >= memory[executionMemY.get()].length) {
          args.add(memory[executionMemY.get() + 1][(executionMemX.get() + k - (memory[executionMemY.get()].length))]);
        } else {
          args.add(memory[executionMemY.get()][executionMemX.get() + k]);
        }
      }

      final boolean executionResult = executeOpcodeService.execute(opcode, args, executionResponse, memory, jumpMap, executeJump, returnPointStack, req.data());

      continueExecutionContext = false;

      int argsOffset = 0;
      if (opcode.equals(Opcodes.RET) || isJumpOpcode(opcode)) {
        final Opcodes jumpOpcode = this.getOpcodesInCursor(executionMemY.get(), executionMemX.get());
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

      if (req.step()) {
        this.currentFlag = Flags.EXECUTING;
        break;
      }
    }

    return new ExecuteResponse(executionResponse.toString(), memory, getRegistersMap(), compiledCode,
      executedY, executedX, this.currentFlag);
  }

  @GetMapping("/state")
  public ExecuteResponse getCurrentState() {
    return new ExecuteResponse(null, memory, getRegistersMap(),
      this.compiledCode, this.executionMemY.get(), this.executionMemX.get(), this.currentFlag);
  }

  @PostMapping("/input")
  public ExecuteResponse processInput(@RequestBody ExecuteReq request) throws InterruptedException {
    return this.executeCode(request, true);
  }

  public static String getHexString(int integer) {
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
      case JEQ, JLE, JGT, JGE, JLT, JNN, JMP -> true;
      default -> false;
    };
  }

  private Opcodes getOpcodesInCursor(int memY, int memX) {
    final String funcCode = memory[memY][memX];

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

  private void clearExecutionContext(boolean onlyRam) {
    executionMemY.set(0);
    executionMemX.set(0);
    clearRegisterStacks();
    executionResponse = new StringBuilder();
    clearMemory(onlyRam);
  }

  private Map<String, List<String>> getRegistersMap() {
    Map<String, List<String>> registers = new HashMap<>();

    final Opcodes nextOpcode = this.getOpcodesInCursor(executionMemY.get(), executionMemX.get());

    registers.put("RA", stackToList(getRegisterByCode(Registers.RA.getHexCode()).getStack()));
    registers.put("RB", stackToList(getRegisterByCode(Registers.RB.getHexCode()).getStack()));
    registers.put("RC", stackToList(getRegisterByCode(Registers.RC.getHexCode()).getStack()));
    registers.put("RD", stackToList(getRegisterByCode(Registers.RD.getHexCode()).getStack()));
    if (nextOpcode != null) {
      registers.put("PC", Collections.singletonList(executionMemY.get() + ", " + executionMemX.get() + ": " + nextOpcode.name() + "(" + nextOpcode.getHexCode() + ")"));
    }
    if (!jumpMap.isEmpty()) {
      final ArrayList<String> jumpList = new ArrayList<>();
      for (Map.Entry<String, JumpPoint> entry : jumpMap.entrySet()) {
        jumpList.add(entry.getValue().name() + " (" + entry.getKey() + ")");
      }
      registers.put("SP", jumpList);
    }

    return registers;
  }

  private void clearRegisterStacks() {
    getRegisterByCode(Registers.RA.getHexCode()).getStack().clear();
    getRegisterByCode(Registers.RB.getHexCode()).getStack().clear();
    getRegisterByCode(Registers.RC.getHexCode()).getStack().clear();
    getRegisterByCode(Registers.RD.getHexCode()).getStack().clear();
  }

  private void clearMemory(boolean onlyRam) {
    for (int y = onlyRam ? romOffset : 0; y < memory.length; y++) {
      for (int x = 0; x < memory[y].length; x++) {
        memory[y][x] = null;
      }
    }
  }

  private String buildCompiledCode(boolean highlightExecutionContext) {
    final StringBuilder compiledCode = new StringBuilder();
    compiledCode.append("-- BEGIN --\n");
    int y = 0;
    int x = 0;

    while (memory[y][x] != null) {

      compiledCode.append("\n");
      if (highlightExecutionContext && y == executionMemY.get() && x == executionMemX.get()) {
        compiledCode.append("-->");
      }
      compiledCode.append(memory[y][x]);

      final Opcodes opcode = this.getOpcodesInCursor(y, x);

      for (int k = 1; k <= opcode.getExpectedArgs(); k++) {
        compiledCode.append(" ");
        if (x + k >= memory[y].length) {
          compiledCode.append(memory[y + 1][(x + k - (memory[y].length))]);
        } else {
          compiledCode.append(memory[y][x + k]);
        }
      }

      for (int i = 5; i > opcode.getExpectedArgs(); i--) {
        compiledCode.append("       ");
      }
      compiledCode.append("-- " + opcode.name());

      // Next function
      if (x + opcode.getExpectedArgs() + 1 >= memory[y].length) {
        if (y + 1 >= memory.length) {
          throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "ROM memory Overflow");
        }
        y = y + 1;
        x = x + opcode.getExpectedArgs() - (memory[y].length - 1);
      } else {
        x = x + opcode.getExpectedArgs() + 1;
      }
    }

    compiledCode.append("\n\n-- END --");
    return compiledCode.toString();
  }

  public record ProcessReq(String sourceCode) {
  }

  public record ExecuteReq(String data, boolean step) {
  }

  public record ProcessResponse(String data, String[][] memoryState, Map<String, List<String>> registers) {
  }

  public record ExecuteResponse(String output, String[][] memoryState, Map<String, List<String>> registers,
                                String compiledCode, int executionY, int executionX,
                                Flags executionFlag) {
  }

  public record JumpPlaceholder(int memY, int memX, String placeholderTxt, String originalName) {
  }

}
