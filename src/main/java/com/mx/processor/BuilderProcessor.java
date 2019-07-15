package com.mx.processor;

import com.mx.ann.Builder;
import com.mx.exception.ProcessingException;
import com.mx.utils.ElementUtils;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.*;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.Collections;
import java.util.Set;

/**
 * @author milo
 */

@SupportedAnnotationTypes("com.mx.ann.Builder")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class BuilderProcessor extends AbstractProcessor {

    static final String THIS = "this";
    static final String TYPE_PREFIX = "H";
    static final String BUILDER_METHOD = "builder";
    static final String BUILD_METHOD = "build";

    private Messager messager;

    JavacTrees trees;

    TreeMaker treeMaker;

    Names names;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        messager = processingEnv.getMessager();
        trees = JavacTrees.instance(processingEnv);
        Context context = ((JavacProcessingEnvironment) processingEnv).getContext();
        treeMaker = TreeMaker.instance(context);
        names = Names.instance(context);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        try {
            Set<? extends Element> elementsAnnotatedWith = roundEnv.getElementsAnnotatedWith(Builder.class);
            for (Element element : elementsAnnotatedWith) {
                if (!ElementUtils.isClass(element)) {
                    throw new ProcessingException(element, "Only classes can be annotated with @%s",
                            Builder.class.getSimpleName());
                }

                //1. is public class
                //3. class have an all args constructor
                checkValidClass((TypeElement) element);

                JCTree jcTree = trees.getTree(element);
                //mk builder
                jcTree.accept(new TreeTranslator() {
                    @Override
                    public void visitClassDef(JCTree.JCClassDecl jcClass) {
                        info("@Builder process [" + jcClass.name.toString() + "] begin");
                        Name className = jcClass.name;
                        List<JCTree.JCVariableDecl> classFields = getAllFields(jcClass);
                        // create TRUE & FALSE phantom type
                        JCTree.JCClassDecl TRUE = createPhantomType("TRUE");
                        JCTree.JCClassDecl FALSE = createPhantomType("FALSE");
                        // --- create Builder class ---
                        Name builderClassName = names.fromString("Builder");
                        List<JCTree.JCTypeParameter> builderClassTypeParams = createBuilderClassTypeParams(classFields);
                        // copy all fields
                        List<JCTree.JCVariableDecl> builderClassFieldDefs = copyAllFields(classFields);
                        // create empty constructor
                        JCTree.JCMethodDecl builderClassEmptyConstructor = createEmptyConstructor();
                        // create all args constructor
                        JCTree.JCMethodDecl builderClassAllArgsConstructor = createAllArgsConstructor(classFields);
                        // create all setter methods
                        List<JCTree.JCMethodDecl> setters = createAllSetter(builderClassName, classFields);
                        // create Builder class
                        JCTree.JCClassDecl builderClass = createBuilderClass(
                                builderClassName,
                                builderClassTypeParams,
                                builderClassFieldDefs,
                                builderClassEmptyConstructor,
                                builderClassAllArgsConstructor,
                                setters);
                        // --- end Builder class ---
                        // create static build method
                        JCTree.JCMethodDecl staticBuildMethod = createStaticBuildMethod(className,
                                builderClassName,
                                defaultTypeArgs(classFields, "TRUE"),
                                classFields);
                        // create static builder method
                        JCTree.JCMethodDecl staticBuilderMethod =
                                createStaticBuilderMethod(builderClassName, defaultTypeArgs(classFields, "FALSE"));
                        // add phantom type
                        jcClass.defs = jcClass.defs.append(TRUE);
                        jcClass.defs = jcClass.defs.append(FALSE);
                        // add builder class
                        jcClass.defs = jcClass.defs.append(builderClass);
                        // add static build method
                        jcClass.defs = jcClass.defs.append(staticBuildMethod);
                        // add static builder method
                        jcClass.defs = jcClass.defs.append(staticBuilderMethod);
//                        jcClass.defs = jcClass.defs.append(testMethod());
                        info("@Builder process [" + jcClass.name.toString() + "] end");
                        //super.visitClassDef(jcClass);
                    }
                });
                //System.out.println(jcTree);
            }
        } catch (ProcessingException e) {
            error(e.getElement(), e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            error(null, e.getMessage());
        }

        return true;
    }

    private void error(Element e, String msg) {
        messager.printMessage(Diagnostic.Kind.ERROR, msg, e);
    }

    private void info(String msg) {
        messager.printMessage(Diagnostic.Kind.NOTE, msg);
    }

    private void checkValidClass(TypeElement classElement) throws ProcessingException {
        // Check if it's an public class
        if (!ElementUtils.isPublicClass(classElement)) {
            throw new ProcessingException(classElement, "The target %s is not public class.",
                    classElement.getQualifiedName().toString());
        }

        // Check if all args public constructor is given
        if (!ElementUtils.hasAllArgsConstructor(classElement, Collections.emptySet())) {
            throw new ProcessingException(classElement,
                    "The class %s must provide an all args constructor",
                    classElement.getQualifiedName().toString());
        }
    }

    private JCTree.JCClassDecl createPhantomType(String name) {
        return treeMaker.ClassDef(
                treeMaker.Modifiers(Flags.STATIC + Flags.ABSTRACT),
                names.fromString(name),
                List.nil(),
                null,
                List.nil(),
                List.nil());
    }

    // TODO mv to utils
    private List<JCTree.JCVariableDecl> getAllFields(JCTree.JCClassDecl jcClass) {
        ListBuffer<JCTree.JCVariableDecl> jcVariables = new ListBuffer<>();
        jcClass.defs.forEach(jcTree -> {
            if (isValidField(jcTree)) {
                jcVariables.append((JCTree.JCVariableDecl) jcTree);
            }
        });
        return jcVariables.toList();
    }

    // TODO mv to utils
    private boolean isValidField(JCTree jcTree) {
        if (jcTree.getKind().equals(JCTree.Kind.VARIABLE)) {
            JCTree.JCVariableDecl jcVariable = (JCTree.JCVariableDecl) jcTree;

            Set<Modifier> flagSets = jcVariable.mods.getFlags();
            return (!flagSets.contains(Modifier.STATIC));
        }
        return false;
    }

    private List<JCTree.JCVariableDecl> copyAllFields(List<JCTree.JCVariableDecl> fields) {
        ListBuffer<JCTree.JCVariableDecl> jcVariables = new ListBuffer<>();
        fields.stream()
                .map(field -> treeMaker.VarDef(
                        treeMaker.Modifiers(Flags.PRIVATE), names.fromString(field.name.toString()), field.vartype, null))
                .forEach(jcVariables::append);
        return jcVariables.toList();
    }

    private JCTree.JCMethodDecl createEmptyConstructor() {
        JCTree.JCBlock emptyBlock = treeMaker.Block(0, List.nil());
        return treeMaker.MethodDef(
                treeMaker.Modifiers(Flags.PRIVATE),
                names.fromString("<init>"),
                treeMaker.TypeIdent(TypeTag.VOID),
                List.nil(),
                List.nil(),
                List.nil(),
                emptyBlock,
                null);
    }

    private JCTree.JCMethodDecl createAllArgsConstructor(List<JCTree.JCVariableDecl> fields) {
        ListBuffer<JCTree.JCStatement> assigns = new ListBuffer<>();
        fields.stream().map(field ->
            treeMaker.Exec(
                    treeMaker.Assign(
                            treeMaker.Select(
                                    treeMaker.Ident(names.fromString(THIS)),
                                    field.name
                            ),
                            treeMaker.Ident(field.name)
                    )
            )
        ).forEach(assigns::append);
        JCTree.JCBlock block = treeMaker.Block(0, assigns.toList());
        List<JCTree.JCVariableDecl> params = createArgs(fields);
        return treeMaker.MethodDef(
                treeMaker.Modifiers(Flags.PRIVATE),
                names.fromString("<init>"),
                treeMaker.TypeIdent(TypeTag.VOID),
                List.nil(),
                params,
                List.nil(),
                block,
                null);
    }

    private List<JCTree.JCMethodDecl> createAllSetter(Name className, List<JCTree.JCVariableDecl> fields) {
        ListBuffer<JCTree.JCMethodDecl> setters = new ListBuffer<>();
        for (JCTree.JCVariableDecl field : fields) {
            // Builder<..., TRUE, ...>
            List<JCTree.JCExpression> typeArgs = createTypeArgs(field, fields); // Builder type args
            JCTree.JCExpression returnType = treeMaker.TypeApply(treeMaker.Ident(className), typeArgs);

            ListBuffer<JCTree.JCExpression> constructorArgs = new ListBuffer<>();
            fields.forEach(arg -> {
                if (arg.name.toString().equals(field.name.toString())) {
                    // use arg
                    constructorArgs.append(treeMaker.Ident(arg.name));
                } else {
                    // use this.field as arg
                    constructorArgs.append(treeMaker.Select(
                            treeMaker.Ident(names.fromString(THIS)),
                            names.fromString(arg.name.toString())
                    ));
                }
            });
            // 方法体
            ListBuffer<JCTree.JCStatement> statements = new ListBuffer<>();
            // this.field1 = arg1
            statements.append(treeMaker.Exec(
                    treeMaker.Assign(
                            treeMaker.Select(
                                    treeMaker.Ident(names.fromString(THIS)),
                                    names.fromString(field.name.toString())
                            ),
                            treeMaker.Ident(names.fromString(field.name.toString()))
                    )
            ));
            // return new Builder<..., TRUE, ...>(arg1, ...);
            statements.append(treeMaker.Return(
                    treeMaker.NewClass(
                            null,
                            List.nil(),
                            treeMaker.TypeApply(treeMaker.Ident(className), typeArgs),
                            constructorArgs.toList(),
                            null
                    )
            ));
            JCTree.JCBlock block = treeMaker.Block(0, statements.toList()); // method body

            setters.append(treeMaker.MethodDef(treeMaker.Modifiers(Flags.PUBLIC),
                    field.name, returnType, List.nil(), createArgs(List.of(field)), List.nil(), block, null));
        }
        return setters.toList();
    }

    private List<JCTree.JCExpression> createTypeArgs(JCTree.JCVariableDecl currField, List<JCTree.JCVariableDecl> fields) {
        ListBuffer<JCTree.JCExpression> typeArgs = new ListBuffer<>();
        for (JCTree.JCVariableDecl field : fields) {
            if (currField.name.toString().equals(field.name.toString())) {
                typeArgs.append(treeMaker.Ident(names.fromString("TRUE")));
            } else {
                typeArgs.append(treeMaker.Ident(names.fromString(TYPE_PREFIX + field.name.toString().toUpperCase())));
            }
        }
        return typeArgs.toList();
    }

    private List<JCTree.JCVariableDecl> createArgs(List<JCTree.JCVariableDecl> fields) {
        ListBuffer<JCTree.JCVariableDecl> typeArgs = new ListBuffer<>();
        fields.stream().map(field -> treeMaker.VarDef(
                treeMaker.Modifiers(Flags.PARAMETER), field.name, field.vartype, null))
                .forEach(typeArgs::append);
        return typeArgs.toList();
    }

    private List<JCTree.JCExpression> defaultTypeArgs(List<JCTree.JCVariableDecl> fields, String typeName) {
        ListBuffer<JCTree.JCExpression> typeArgs = new ListBuffer<>();
        fields.stream().map(__ -> treeMaker.Ident(names.fromString(typeName))).forEach(typeArgs::append);
        return typeArgs.toList();
    }

    private List<JCTree.JCTypeParameter> createBuilderClassTypeParams(List<JCTree.JCVariableDecl> fields) {
        ListBuffer<JCTree.JCTypeParameter> typeParams = new ListBuffer<>();
        fields.stream()
                .map(field ->
                        treeMaker.TypeParameter(names.fromString(TYPE_PREFIX + field.name.toString().toUpperCase()), List.nil()))
                .forEach(typeParams::append);
        return typeParams.toList();
    }

    private JCTree.JCClassDecl createBuilderClass(Name className,
                                                  List<JCTree.JCTypeParameter> builderClassTypeParams,
                                                  List<JCTree.JCVariableDecl> builderClassFieldDefs,
                                                  JCTree.JCMethodDecl builderClassEmptyConstructor,
                                                  JCTree.JCMethodDecl builderClassAllArgsConstructor,
                                                  List<JCTree.JCMethodDecl> setters) {
        ListBuffer<JCTree> builderClassBody = new ListBuffer<>();
        builderClassBody.appendList(List.convert(JCTree.class, builderClassFieldDefs))
                .append(builderClassEmptyConstructor)
                .append(builderClassAllArgsConstructor)
                .appendList(List.convert(JCTree.class, setters));

        return treeMaker.ClassDef(
                treeMaker.Modifiers(Flags.PUBLIC + Flags.STATIC),
                className,
                builderClassTypeParams,
                null,
                List.nil(),
                builderClassBody.toList());
    }

    private JCTree.JCMethodDecl createStaticBuilderMethod(Name builderClassName, List<JCTree.JCExpression> typeArgs) {

        JCTree.JCExpression returnType = treeMaker.TypeApply(treeMaker.Ident(builderClassName), typeArgs);
        Name methodName = names.fromString(BUILDER_METHOD);
        // return new Builder<..., TRUE, ...>(arg1, ...);
        List<JCTree.JCStatement> statements = List.of(
                treeMaker.Return(
                        treeMaker.NewClass(
                            null,
                            List.nil(),
                            treeMaker.TypeApply(treeMaker.Ident(builderClassName), typeArgs),
                            List.nil(),
                            null
                    )
                ));
        JCTree.JCBlock block = treeMaker.Block(0, statements);
        return treeMaker.MethodDef(treeMaker.Modifiers(Flags.PUBLIC + Flags.STATIC),
                methodName, returnType, List.nil(), List.nil(), List.nil(), block, null);
    }

    private JCTree.JCMethodDecl createStaticBuildMethod(Name className,
                                                        Name builderClassName,
                                                        List<JCTree.JCExpression> typeArgs,
                                                        List<JCTree.JCVariableDecl> fields) {
        JCTree.JCExpression returnType = treeMaker.Ident(className);
        JCTree.JCExpression argType = treeMaker.TypeApply(treeMaker.Ident(builderClassName), typeArgs);
        List<JCTree.JCVariableDecl> params = List.of(
                treeMaker.VarDef(treeMaker.Modifiers(Flags.PARAMETER), names.fromString(BUILDER_METHOD), argType, null)
        );
        ListBuffer<JCTree.JCExpression> args = new ListBuffer<>();
        fields.stream().map(field -> treeMaker.Select(treeMaker.Ident(names.fromString(BUILDER_METHOD)), field.name))
                .forEach(args::append);
        Name methodName = names.fromString(BUILD_METHOD);
        // return new Foo(builder.a, builder.b);
        List<JCTree.JCStatement> statements = List.of(
                treeMaker.Return(
                        treeMaker.NewClass(
                                null,
                                List.nil(),
                                treeMaker.Ident(className),
                                args.toList(),
                                null
                        )
                ));
        JCTree.JCBlock block = treeMaker.Block(0, statements);
        return treeMaker.MethodDef(treeMaker.Modifiers(Flags.PUBLIC + Flags.STATIC),
                methodName, returnType, List.nil(), params, List.nil(), block, null);
    }

    private JCTree.JCMethodDecl testMethod() {
        JCTree.JCBlock block = treeMaker.Block(0, List.nil());
        List<JCTree.JCVariableDecl> params = List.of(treeMaker.VarDef(treeMaker.Modifiers(Flags.PARAMETER), names.fromString("x"), treeMaker.TypeIdent(TypeTag.INT), null));
        return treeMaker.MethodDef(treeMaker.Modifiers(Flags.PUBLIC),
                names.fromString("test"),
                treeMaker.TypeIdent(TypeTag.VOID),
                List.nil(),
                params,
                List.nil(),
                block,
                null);
    }

}
