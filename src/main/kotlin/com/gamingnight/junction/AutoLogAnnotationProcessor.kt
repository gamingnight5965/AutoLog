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
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.writeTo
import java.util.*


class AutoLogAnnotationProcessor(
    val codeGenerator: CodeGenerator, val logger: KSPLogger
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val annotatedClasses = resolver.getSymbolsWithAnnotation("org.littletonrobotics.junction.AutoLog")
        val ret = annotatedClasses.filter { !it.validate() }.toList()

        annotatedClasses.filter { it is KSClassDeclaration && it.validate() }
            .forEach { it.accept(AutoLogVisitor(), Unit) }

        return ret

    }

    inner class AutoLogVisitor : KSVisitorVoid() {


        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
            if (Modifier.OPEN !in classDeclaration.modifiers) logger.error(
                "Auto Logged Classes Must Be Open", classDeclaration
            )

            val LOG_TABLE_TYPE: TypeName =
                ClassName("org.littletonrobotics.junction", "LogTable")
            val LOGGABLE_INPUTS_TYPE: TypeName = ClassName(
                "org.littletonrobotics.junction.inputs", "LoggableInputs"
            )


            val LOGGABLE_TYPE_LOOKUP: Map<String, String> = hashMapOf(
                "Boolean" to "Boolean",
                "Long" to "Integer",
                "Float" to "Float",
                "Double" to "Double",
                "String" to "String",
            )

            val LOGGALE_LIST_TYPE_LOOKUP: Map<String, String> = hashMapOf(
                "Byte" to "Raw",
                "Boolean" to "BooleanArray",
                "Long" to "IntegerArray",
                "Float" to "FloatArray",
                "Double" to "DoubleArray",
                "String" to "StringArray"
            )

            val UNLOGGABLE_TYPES_LOOKUP: Map<String, String> = hashMapOf(
                "MutableList" to "List", "Int" to "Long"
            )

            val packageName = classDeclaration.containingFile!!.packageName.asString()
            val autoLoggedClassName: String = "${classDeclaration.simpleName.asString()}AutoLogged"

            val toLogBuilder =
                FunSpec.builder("toLog").addModifiers(KModifier.OVERRIDE).addParameter("table", LOG_TABLE_TYPE)

            val fromLogBuilder =
                FunSpec.builder("fromLog").addModifiers(KModifier.OVERRIDE).addParameter("table", LOG_TABLE_TYPE)

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
                    when (val type = fieldType.arguments.first().type!!.resolve().declaration.simpleName.asString()) {
                        "String" -> ".toTypedArray()"
                        else -> ".to${type}Array()"
                    }
                } else ""

                val fromLogConversion = if (fieldType.arguments.isNotEmpty()) ".asList()" else ""

                if (logType == null) {
                    val typeSuggestion = UNLOGGABLE_TYPES_LOOKUP[fieldType.declaration.simpleName.asString()]
                        ?: UNLOGGABLE_TYPES_LOOKUP[fieldType.arguments.firstOrNull()?.type!!.resolve().declaration.simpleName.asString()]
                    var extraText = if (typeSuggestion != null) "Did you mean to use\"$typeSuggestion\" instead?"
                    else "\"${fieldType.declaration.simpleName.asString()}\" is not supported"

                    System.err.println(
                        "[org.frc1778.junction.AutoLog] Unkonwn type for \"" + simpleName + "\" from \"" + classDeclaration.simpleName.asString() + "\" (" + extraText + ")"
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











