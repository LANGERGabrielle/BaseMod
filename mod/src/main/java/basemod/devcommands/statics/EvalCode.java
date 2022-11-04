package basemod.devcommands.statics;

import basemod.DevConsole;
import basemod.devcommands.ConsoleCommand;
import com.evacipated.cardcrawl.modthespire.Loader;
import javassist.*;
import javassist.bytecode.*;
import javassist.compiler.MemberCodeGen;
import javassist.convert.Transformer;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;
import javassist.expr.MethodCall;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

//Invoke code and output the result as toString
public class EvalCode extends ConsoleCommand
{
    public static class OurCode {}

    private static ClassLoader loader = null;

    public EvalCode() {
        minExtraTokens = 1;
        maxExtraTokens = 0;
        simpleCheck = false;
    }

    private static void loadAll(CtClass ctClass) throws NotFoundException, CannotCompileException
    {
        for (CtClass nested : ctClass.getNestedClasses()) {
            loadAll(nested);
        }
        ctClass.toClass(loader, null);
    }

    @Override
    public void execute(String[] tokens, int depth) {
        if (loader == null) {
            try {
                // Create a new ClassLoader just containing MTS, because that's where javassist is located
                loader = new URLClassLoader(new URL[] {Loader.class.getProtectionDomain().getCodeSource().getLocation()}, null);

                ClassPool pool = new ClassPool();
                pool.appendSystemPath();
                pool.insertClassPath(new LoaderClassPath(EvalCode.class.getClassLoader()));
                pool.importPackage("javassist.bytecode");

                // Patch to disable the javassist compiler's check for private fields
                CtClass ctMemberCodeGen = pool.get(MemberCodeGen.class.getName());
                CtMethod isAccessibleField = ctMemberCodeGen.getDeclaredMethod("isAccessibleField");
                isAccessibleField.insertAt(942, "return null;");

                // Patch to disable the javassist compiler's check for private methods
                CtMethod atMethodCallCore2 = ctMemberCodeGen.getDeclaredMethod("atMethodCallCore2");
                atMethodCallCore2.insertAt(601, "if (AccessFlag.isPrivate(acc)) {" +
                        "isSpecial = true;" +
                        "acc = AccessFlag.setPackage(acc);" +
                        "}");

                // Load CodeRunner and its prerequisites into the new ClassLoader
                ctMemberCodeGen.toClass(loader, null);
                CtClass ctCodeRunner = pool.get("basemod.devcommands.statics.EvalCode$CodeRunner");
                loadAll(ctCodeRunner);
                pool.get(OurCode.class.getName()).toClass(loader, null);
            } catch (NotFoundException | CannotCompileException e) {
                DevConsole.log("Something bad happened. Check the log");
                e.printStackTrace();
                return;
            }
        }

        String src =
                "public static Object eval() {" +
                "return " + String.join(" ", Arrays.copyOfRange(tokens, 1, tokens.length));
        if (!tokens[1].endsWith(";")) {
            src += ";";
        }
        src += "}";

        try {
            // Get CodeRunner and run it in our ClassLoader
            Class<?> codeRunner = loader.loadClass("basemod.devcommands.statics.EvalCode$CodeRunner");
            Method run = codeRunner.getDeclaredMethod("run", String.class, ClassLoader.class, boolean.class);
            run.setAccessible(true);
            Object output = run.invoke(null, src, EvalCode.class.getClassLoader(), Loader.DEBUG);
            if (output instanceof CannotCompileException) {
                CannotCompileException e = (CannotCompileException) output;
                DevConsole.log("Cannot compile: " + e.getMessage());
                e.printStackTrace();
            } else if (output instanceof InvocationTargetException) {
                InvocationTargetException e = (InvocationTargetException) output;
                DevConsole.log("Exception in eval code: " + e.getCause());
                e.getCause().printStackTrace();
            } else if (output instanceof Exception || output instanceof Error){
                Throwable e = (Throwable) output;
                DevConsole.log("Something bad happened. Check the log");
                e.printStackTrace();
            }
            if (output == null) {
                DevConsole.log("Output: null");
            } else {
                DevConsole.log("Output: " + output);
            }
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    private static class CodeRunner
    {
        private static ClassPool pool = null;
        private static int evalCount = 0;

        public static Object run(String src, ClassLoader outLoader, boolean debug)
        {
            if (pool == null) {
                pool = new ClassPool();
                pool.appendSystemPath();
                pool.insertClassPath(new LoaderClassPath(outLoader));
                pool.childFirstLookup = true;

                pool.importPackage("com.megacrit.cardcrawl");
                pool.importPackage("com.megacrit.cardcrawl.core");
                pool.importPackage("com.megacrit.cardcrawl.dungeons");
                pool.importPackage("com.megacrit.cardcrawl.cards");
                pool.importPackage("com.megacrit.cardcrawl.powers");
                pool.importPackage("com.megacrit.cardcrawl.relics");
            }

            if (debug) {
                System.out.println(src);
            }

            try {
                // Make a new class to contain the new code
                CtClass ctClass = pool.makeClass(OurCode.class.getName() + "$Eval" + evalCount);
                ++evalCount;

                CtMethod ctMethod = CtNewMethod.make(src, ctClass);
                ctClass.addMethod(ctMethod);
                CodeAttribute code = ctMethod.getMethodInfo().getCodeAttribute();
                boolean stackUnderflow = false;
                try {
                    code.computeMaxStack();
                } catch (BadBytecode e) {
                    if (e.getMessage().startsWith("stack underflow")) {
                        stackUnderflow = true;
                    } else {
                        throw e;
                    }
                }
                // Convert any illegal accessing of protected/private fields/methods to use reflection
                ctMethod.instrument(new FixIllegalAccess(debug));
                // Fix return when the code doesn't have a return
                ctMethod.instrument(new ReturnCodeConvertor(debug, stackUnderflow));

                if (debug) {
                    ctClass.debugWriteFile("evalcode_debug");
                }
                // Load the new class into the normal ClassLoader, so it has access to the game's actual state
                Class<?> cls = ctClass.toClass(outLoader, null);
                Method method = cls.getMethod("eval");
                return method.invoke(null);
            } catch (Exception | Error e) {
                return e;
            }
        }

        private static class FixIllegalAccess extends ExprEditor
        {
            private final boolean debug;

            FixIllegalAccess(boolean debug)
            {
                this.debug = debug;
            }

            private boolean canAccess(CtClass thatClass, int modifiers)
            {
                return Modifier.isPublic(thatClass.getModifiers() & modifiers);
            }

            @Override
            public void edit(MethodCall m) throws CannotCompileException
            {
                try {
                    CtMethod method = m.getMethod();
                    if (!canAccess(method.getDeclaringClass(), method.getModifiers())) {
                        List<String> argTypes = Arrays.stream(method.getParameterTypes())
                                .map(CtClass::getName)
                                .map(x -> x + ".class")
                                .collect(Collectors.toList());
                        String src = "java.lang.reflect.Method m = " + m.getClassName() + ".class.getDeclaredMethod(\"" + method.getName() + "\"";
                        if (argTypes.size() > 0) {
                            src += ", new Class[] {" + String.join(", ", argTypes) + "}";
                        } else {
                            src += ", new Class[0]";
                        }
                        src += ");\n";
                        src += "m.setAccessible(true);\n";
                        src += "$_ = ($r) m.invoke($0, $args);\n";
                        if (debug) {
                            System.out.println(src);
                        }
                        m.replace(src);
                    }
                } catch (NotFoundException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void edit(FieldAccess f) throws CannotCompileException
            {
                try {
                    CtField field = f.getField();
                    if (!canAccess(field.getDeclaringClass(), field.getModifiers())) {
                        String src = "java.lang.reflect.Field f = " + f.getClassName() + ".class.getDeclaredField(\"" + field.getName() + "\");\n";
                        src += "f.setAccessible(true);\n";
                        if (f.isReader()) {
                            src += "$_ = ($r) f.get($0);\n";
                        } else {
                            src += "f.set($0, ($w) $1);\n";
                        }
                        if (debug) {
                            System.out.println(src);
                        }
                        f.replace(src);
                    }
                } catch (NotFoundException e) {
                    e.printStackTrace();
                }
            }
        }

        private static class ReturnCodeConvertor extends CodeConverter
        {
            private final boolean debug;

            public ReturnCodeConvertor(boolean debug, boolean stackUnderflow)
            {
                this.debug = debug;
                transformers = new TransformPrimitiveReturn(transformers, stackUnderflow);
            }

            private static class TransformPrimitiveReturn extends Transformer
            {
                private final boolean voidReturn;

                public TransformPrimitiveReturn(Transformer t, boolean stackUnderflow)
                {
                    super(t);
                    voidReturn = stackUnderflow;
                }

                @Override
                public int transform(CtClass clazz, int pos, CodeIterator iterator,
                                     ConstPool cp) throws CannotCompileException, BadBytecode
                {
                    ClassPool pool = clazz.getClassPool();
                    int c = iterator.byteAt(pos);
                    CtPrimitiveType primitiveType = null;
                    if (c == IRETURN && voidReturn) {
                        c = RETURN;
                    }
                    switch (c) {
                        case IRETURN: // int
                            primitiveType = (CtPrimitiveType) CtClass.intType;
                            break;
                        case LRETURN: // long
                            primitiveType = (CtPrimitiveType) CtClass.longType;
                            break;
                        case FRETURN: // float
                            primitiveType = (CtPrimitiveType) CtClass.floatType;
                            break;
                        case DRETURN: // double
                            primitiveType = (CtPrimitiveType) CtClass.doubleType;
                            break;
                        case RETURN: // void
                        {
                            iterator.writeByte(ARETURN, pos);
                            Bytecode bytecode = new Bytecode(cp);
                            bytecode.add(ACONST_NULL);
                            iterator.insert(pos, bytecode.get());
                            break;
                        }
                    }
                    if (primitiveType != null) {
                        iterator.writeByte(NOP, pos);
                        Bytecode bytecode = new Bytecode(cp);
                        try {
                            CtClass wrapperType = pool.get(primitiveType.getWrapperName());
                            bytecode.addInvokestatic(wrapperType, "valueOf", wrapperType, new CtClass[] { primitiveType });
                        } catch (NotFoundException e) {
                            throw new RuntimeException(e);
                        }
                        bytecode.add(ARETURN);
                        iterator.insert(pos + 1, bytecode.get());
                    }
                    return pos;
                }
            }
        }
    }

    @Override
    public void errorMsg() {
        DevConsole.couldNotParse();
        DevConsole.log("");
    }
}
