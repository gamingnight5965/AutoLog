package org.frc1778.junction
import AutoLogAnnotationProcessor
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

class AutoLogAnnotationProcessorProvider: SymbolProcessorProvider {
	override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
		return AutoLogAnnotationProcessor(environment.codeGenerator, environment.logger)
	}
}