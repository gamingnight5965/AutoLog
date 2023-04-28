package com.gamingnight.junction

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.DelicateKotlinPoetApi
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.javapoet.KTypeName
import com.squareup.kotlinpoet.javapoet.KotlinPoetJavaPoetPreview
import com.squareup.kotlinpoet.javapoet.toJTypeName
import com.squareup.kotlinpoet.javapoet.toKTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import java.util.*


class AutoLogAnnotationProcessor(
    val codeGenerator: CodeGenerator, val logger: KSPLogger
) : SymbolProcessor {

    @OptIn(DelicateKotlinPoetApi::class)
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val annotatedClasses = resolver.getSymbolsWithAnnotation(AutoLog::class.qualifiedName!!)
        val ret = annotatedClasses.filter { !it.validate() }.toList()

        annotatedClasses.filter { it is KSClassDeclaration && it.validate() }
            .forEach { it.accept(AutoLogVisitor(), Unit) }

        return ret

    }

    inner class AutoLogVisitor : KSVisitorVoid() {


        @OptIn(KotlinPoetJavaPoetPreview::class)
        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {

            val LOG_TABLE_TYPE: KTypeName =
                ClassName("org.littletonrobotics.junction", "LogTable").toJTypeName().toKTypeName()
            val LOGGABLE_INPUTS_TYPE: KTypeName = ClassName(
                "org.littletonrobotics.junction.inputs", "LoggableInputs"
            ).toJTypeName().toKTypeName()


            val LOGGABLE_TYPE_LOOKUP: MutableMap<String, String> = hashMapOf(
                "Boolean" to "Boolean",
                "Long" to "Integer",
                "Float" to "Float",
                "Double" to "Double",
                "String" to "String",
            )

            val LOGGALE_LIST_TYPE_LOOKUP: MutableMap<String, String> = hashMapOf(
                "Byte" to "Raw",
                "Boolean" to "BooleanArray",
                "Long" to "IntegerArray",
                "Float" to "FloatArray",
                "Double" to "DoubleArray",
                "String" to "StringArray"
            )

            val packageName = classDeclaration.containingFile!!.packageName.asString()
            val autoLoggedClassName: String = "${classDeclaration.simpleName.asString()}AutoLogged"

            val toLogBuilder =
                FunSpec.builder("toLog").addModifiers(KModifier.OVERRIDE).addParameter("table", LOG_TABLE_TYPE)

            val fromLogBuilder = FunSpec.builder("fromLog").addModifiers(KModifier.OVERRIDE).addParameter("table", LOG_TABLE_TYPE)

            val cloneBuilder = FunSpec.builder("clone")
                .addCode("val copy: %L = %L()\n", autoLoggedClassName, autoLoggedClassName)
                .returns(ClassName(packageName, autoLoggedClassName))

            classDeclaration.declarations.filterIsInstance<KSPropertyDeclaration>().forEach { fieldElement ->
                val simpleName: String = fieldElement.simpleName.asString()
                val logName =
                    simpleName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }



                val fieldType = fieldElement.type.resolve()
                val logType: String? = if (fieldType.declaration.simpleName.asString() != "List") {
                    LOGGABLE_TYPE_LOOKUP[fieldType.declaration.simpleName.asString()]
                } else {
                    LOGGALE_LIST_TYPE_LOOKUP[fieldType.arguments.first().type!!.resolve().declaration.simpleName.asString()]
                }
                val getterName = "get$logType"

                val toLogConversion = if (fieldType.arguments.isNotEmpty()) {
                    ".to${fieldType.arguments.first().type!!.resolve().declaration.simpleName.asString()}Array()"
                } else ""

                val fromLogConversion = if (fieldType.arguments.isNotEmpty()) ".asList()" else ""

                if (logType == null) {
                    System.err.println(
                        "[org.frc1778.junction.AutoLog] Unkonwn type for \"" + simpleName + "\" from \"" + classDeclaration.simpleName.asString() + "\" (" + fieldElement.type.resolve().declaration.simpleName + ")"
                    )
                } else {
                    toLogBuilder.addCode("table.put(%S, %L)\n", logName, simpleName + toLogConversion)

                    fromLogBuilder.addCode(
                        "%L = table.%L(%S, %L)%L\n",
                        simpleName,
                        getterName,
                        logName,
                        simpleName + toLogConversion,
                        fromLogConversion
                    )

                    cloneBuilder.addCode(
                        "copy.%L = this.%L\n", simpleName, simpleName
                    )
                }
            }
            cloneBuilder.addCode("return copy\n")

            val type = TypeSpec.classBuilder(autoLoggedClassName)
                .superclass(ClassName(packageName, classDeclaration.simpleName.asString()))
                .addSuperinterface(LOGGABLE_INPUTS_TYPE)
                .addSuperinterface(ClassName("java.lang", "Cloneable"))
                .addFunction(toLogBuilder.build())
                .addFunction(fromLogBuilder.build())
                .addFunction(cloneBuilder.build())
                .build()

            val kotlinFile = FileSpec.builder(packageName, autoLoggedClassName).addType(type).build()

            try {
                kotlinFile.writeTo(codeGenerator, Dependencies(true, classDeclaration.containingFile!!))

            } catch (e: Exception) {
                logger.error("Error writing file: $e")
                e.printStackTrace()
            }
        }

    }


}










