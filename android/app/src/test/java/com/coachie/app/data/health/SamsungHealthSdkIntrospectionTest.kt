package com.coachie.app.data.health

import com.samsung.android.sdk.health.data.request.DataTypes
import kotlin.reflect.full.memberProperties
import org.junit.Test

class SamsungHealthSdkIntrospectionTest {
    @Test
    fun logReadDataRequestBuilderMethods() {
        val builderClass = Class.forName("com.samsung.android.sdk.health.data.request.ReadDataRequest\$Builder")
        println("ReadDataRequest.Builder declared fields:")
        builderClass.declaredFields.sortedBy { it.name }.forEach { field ->
            println("  ${field.toGenericString()}")
        }
        println("ReadDataRequest.Builder declared methods:")
        builderClass.declaredMethods.sortedBy { it.name }.forEach { method ->
            println("  ${method.toGenericString()}")
        }
        println("ReadDataRequest.Builder public methods:")
        builderClass.methods.sortedBy { it.name }.forEach { method ->
            println("  ${method.toGenericString()}")
        }
        println("ReadDataRequest.Builder constructors:")
        builderClass.constructors.forEach { ctor ->
            println("  ${ctor.toGenericString()}")
        }

        try {
            val companionClass = Class.forName("com.samsung.android.sdk.health.data.request.ReadDataRequest\$Companion")
            println("ReadDataRequest.Companion methods:")
            companionClass.methods.sortedBy { it.name }.forEach { method ->
                println("  ${method.toGenericString()}")
            }
        } catch (ex: ClassNotFoundException) {
            println("ReadDataRequest.Companion not found: ${ex.message}")
        }
    }

    @Test
    fun logAggregateRequestBuilderMethods() {
        val builderClass = Class.forName("com.samsung.android.sdk.health.data.request.AggregateRequest\$Builder")
        println("AggregateRequest.Builder declared fields:")
        builderClass.declaredFields.sortedBy { it.name }.forEach { field ->
            println("  ${field.toGenericString()}")
        }
        println("AggregateRequest.Builder declared methods:")
        builderClass.declaredMethods.sortedBy { it.name }.forEach { method ->
            println("  ${method.toGenericString()}")
        }
        println("AggregateRequest.Builder public methods:")
        builderClass.methods.sortedBy { it.name }.forEach { method ->
            println("  ${method.toGenericString()}")
        }

        try {
            val companionClass = Class.forName("com.samsung.android.sdk.health.data.request.AggregateRequest\$Companion")
            println("AggregateRequest.Companion methods:")
            companionClass.methods.sortedBy { it.name }.forEach { method ->
                println("  ${method.toGenericString()}")
            }
        } catch (ex: ClassNotFoundException) {
            println("AggregateRequest.Companion not found: ${ex.message}")
        }
    }

    @Test
    fun logDataTypes() {
        val dataTypesClass = Class.forName("com.samsung.android.sdk.health.data.request.DataTypes")
        println("DataTypes constants:")
        dataTypesClass.fields.sortedBy { it.name }.forEach { field ->
            println("  ${field.name}: ${field.type}")
        }

        val stepsType = DataTypes.STEPS
        println("StepsType Kotlin class: ${stepsType::class.qualifiedName}")
        val allFieldsMethod = stepsType::class.java.methods.firstOrNull { it.name == "getAllFields" }
        val fields = allFieldsMethod?.invoke(stepsType) as? Collection<*>
        println("StepsType fields (reflection):")
        fields?.forEach { field ->
            val fieldClass = field?.javaClass
            val nameMethod = fieldClass?.methods?.firstOrNull { it.name == "getName" }
            val valueTypeMethod = fieldClass?.methods?.firstOrNull { it.name == "getValueType" }
            println("  ${nameMethod?.invoke(field)} - valueType=${valueTypeMethod?.invoke(field)} (${fieldClass?.name})")
        }
        val aggregatesMethod = stepsType::class.java.methods.firstOrNull { it.name == "getAllAggregateOperations" }
        val aggregates = aggregatesMethod?.invoke(stepsType) as? Collection<*>
        println("StepsType aggregate ops (reflection):")
        aggregates?.forEach { op ->
            val opClass = op?.javaClass
            val typeName = opClass?.methods?.firstOrNull { it.name == "getTypeName" }?.invoke(op)
            val operationName = opClass?.methods?.firstOrNull { it.name == "getOperationName" }?.invoke(op)
            val builder = opClass?.methods?.firstOrNull { it.name == "getRequestBuilder" }?.invoke(op)
            val builderKlass = builder?.let { it::class }
            val propertyNames = builderKlass?.memberProperties?.map { it.name }?.sorted()
            println("  type=$typeName operation=$operationName builderClass=${builder?.javaClass?.name} properties=$propertyNames")
        }
    }
}


