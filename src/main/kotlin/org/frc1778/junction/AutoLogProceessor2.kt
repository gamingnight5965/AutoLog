
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.javapoet.KTypeName
import com.squareup.kotlinpoet.javapoet.KotlinPoetJavaPoetPreview
import com.squareup.kotlinpoet.javapoet.toJTypeName
import com.squareup.kotlinpoet.javapoet.toKTypeName

@KotlinPoetJavaPoetPreview
class AutoLogAnnotationProcessor(private val environment: SymbolProcessorEnvironment): SymbolProcessor {
	private val LOG_TABLE_TYPE: KTypeName =
        ClassName("org.littletonrobotics.junction", "LogTable").toJTypeName().toKTypeName()
    private val LOGGABLE_INPUTS_TYPE: KTypeName = ClassName(
        "org.littletonrobotics.junction.inputs", "LoggableInputs"
    ).toJTypeName().toKTypeName()
	
	private fun Resolver.findAnnotations(
		kCLass: KClass<*>
	) = getSymbolsWithAnnotation(
		kClass.qualifiedName.toString()
	).filterIsInstance<KSClassDeclaration>()


	@OptIn(DelicateKotlinPoetApi::class)
	override fun process(resolver: Resolver) : List<KSAnnotated> {
		val annotatedClasses: Sequence<KSClassDeclaration> =
		resolver.findAnnotations(AutoLog::class)

		if(!annotatedClasses.iterator().hasNext()) return emptyList()

		annotatedClasses.iterator().forEachRemaining { classDeclaration: KSClassDeclaration ->
			val packageName = classDeclaration.containingFile!!.packageName.toString()
			val autoLoggedClassName: String  = classDeclaration.simpleName.toKTypeName().toString() + "AutoLogged"

			val toLogBuilder = FunSpec.builder("toLog")
				.addModifiers(KModifier.OVERRIDE)
				.addParameter("table", LOG_TABLE_TYPE)

			val fromLogBuilder = FunSpec
				.builder("fromLog")
				.addParameter("table", LOG_TABLE_TYPE)

				val cloneBuilder = FunSpec.builder("clone")
				.addCode("val copy: %L = %L()\n", autoLoggedClassName, autoLoggedClassName)
			.returns(ClassName(packageName, autoLoggedClassName))

			classDeclaration.declarations.filter {
				element -> element is KSPropertyDeclaration}.forEach {
				fieldElement ->

				val simpleName: String = fieldElement.simpleName.ToString().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
				}

			val logName = simpleName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
			}
		}

		
		




		
		return emptyList()
	}	
}