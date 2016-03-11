package roc
package postgresql
package server

import cats.data.Validated._
import cats.data.{NonEmptyList, Validated, Xor}
import cats.Semigroup
import cats.std.all._
import cats.syntax.eq._
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Prop.forAll
import org.scalacheck.{Arbitrary, Gen}
import org.specs2._
import org.specs2.specification.core._
import org.specs2.specification.create.FragmentsFactory
import roc.postgresql.ErrorResponseDecodingFailure
import roc.postgresql.server.ErrorNoticeMessageFields._

final class PostgresqlErrorSpec extends Specification with ScalaCheck { def is = s2"""

  PostgresqlError
    must extract the value of a tuple by the Code                                     ${PE().testExtractValueByCode}
    must return Xor.Right(UnknownError(ErrorParams)) when given unknown SQLSTATE Code ${PE().testUnknownError}

  ValidatePacket
    must return RequiredParams when fields are valid                             ${VP().testAllValid}
    must return Invalid when Severity is not present                             ${VP().testInvalidSeverity}
    must return Invalid when SQLSTATECODE is not present                         ${VP().testInvalidSqlStateCode}
    must return Invalid when Message is not present                              ${VP().testInvalidMessage}
    must return Invalid when SQLSTATE Code & Message are not present             ${VP().testInvalidSqlStateCodeMessage}
    must return Invalid when Severity & Message are not present                  ${VP().testInvalidSeverityMessage}
    must return Invalid when Severity & SQLSTATE Code are not present            ${VP().testInvalidSeveritySqlStateCode}
    must return Invalid when Severity & SQLSTATE Code & Message are not present  ${VP().testInvalidAll}

  BuildParamsFromTuples
    must return Xor.Right(ErrorParams) when given valid Fields                    ${BPFT().testValidFields}
    must return Xor.Left(ErrorResponseDecodingFailure) when given invalid Fields  ${BPFT().testInvalidFields}
    must have correct Error Message when Severity is invalid                      ${BPFT().testSeverityMessage}
    must have correct Error Message when SQLSTATECode is invalid                  ${BPFT().testSqlStateCodeMessage}
    must have correct Error Message when Message is invalid                       ${BPFT().testMessageMessage}
    must have correct Error Message when Severity and SQLSTATECode is invalid     ${BPFT().testSeveritySqlStateCodeMessage}
    must have correct Error Message when Severity and Message is invalid          ${BPFT().testSeverityMessageMessage}
    must have correct Error Message when SQLSTATECode and Message is invalid      ${BPFT().testSqlStateCodeMessageMessage}
    must have correct Error Message when no required fields are present           ${BPFT().testNoRequiredFieldsFoundMessage}

  ErrorParams
    PostgresqlError must have correct Severity          ${EP().testSeverity}
    PostgresqlError must have correct Code              ${EP().testCode}
    PostgresqlError must have correct Message           ${EP().testMessage}
    PostgresqlError must have correct Detail            ${EP().testDetail}
    PostgresqlError must have correct Hint              ${EP().testHint}
    PostgresqlError must have correct Position          ${EP().testPosition}
    PostgresqlError must have correct InternalPosition  ${EP().testInternalPosition}
    PostgresqlError must have correct InternalQuery     ${EP().testInternalQuery}
    PostgresqlError must have correct Where             ${EP().testWhere}
    PostgresqlError must have correct SchemaName        ${EP().testSchemaName}
    PostgresqlError must have correct TableName         ${EP().testTableName}
    PostgresqlError must have correct ColumnName        ${EP().testColumnName}
    PostgresqlError must have correct DataTypeName      ${EP().testDataTypeName}
    PostgresqlError must have correct ConstraintName    ${EP().testConstraintName}
    PostgresqlError must have correct File              ${EP().testFile}
    PostgresqlError must have correct Line              ${EP().testLine}
    PostgresqlError must have correct Routine           ${EP().testRoutine}
                                                                                    """

  case class PE() extends ErrorNoticeGen {
    val testExtractValueByCode = forAll { container: ExtractValueByCodeContainer =>
      val code     = container.code
      val xs       = container.xs
      val expected = xs.find(_._1 === code).map(_._2)
      PostgresqlError.extractValueByCode(code, xs) must_== expected
    }

    val testUnknownError = forAll(unknownErrorGen) { x: FieldsAndErrorParams =>
      PostgresqlError(x.fields) must_== Xor.Right(UnknownError(x.errorParams)) 
    }
  }

  case class VP() extends ErrorNoticeGen {

    val testAllValid = forAll(validRequiredFieldsGen) { xs: Fields =>
      val severity = extractSeverity(xs)
      val code     = extractCode(xs)
      val message  = extractMessage(xs)

      val actual = PostgresqlError.validatePacket(severity.toValidatedNel, code.toValidatedNel,
        message.toValidatedNel)(RequiredParams.apply)
      val expected = validatePacket(severity.toValidatedNel, code.toValidatedNel,
        message.toValidatedNel)(RequiredParams.apply)

      actual must_== expected
    }

    val testInvalidSeverity = forAll(invalidSeverityFieldsGen) { xs: Fields =>
      val severity = extractSeverity(xs)
      val code     = extractCode(xs)
      val message  = extractMessage(xs)

      val actual = PostgresqlError.validatePacket(severity.toValidatedNel, code.toValidatedNel,
        message.toValidatedNel)(RequiredParams.apply)
      val expected = validatePacket(severity.toValidatedNel, code.toValidatedNel,
        message.toValidatedNel)(RequiredParams.apply)

      actual must_== expected
    }

    val testInvalidSqlStateCode = forAll(invalidSqlStateCodeFieldsGen) { xs: Fields =>
      val severity = extractSeverity(xs)
      val code     = extractCode(xs)
      val message  = extractMessage(xs)

      val actual = PostgresqlError.validatePacket(severity.toValidatedNel, code.toValidatedNel,
        message.toValidatedNel)(RequiredParams.apply)
      val expected = validatePacket(severity.toValidatedNel, code.toValidatedNel,
        message.toValidatedNel)(RequiredParams.apply)

      actual must_== expected
    }

    val testInvalidMessage = forAll(invalidMessageFieldsGen) { xs: Fields => 
      val severity = extractSeverity(xs)
      val code     = extractCode(xs)
      val message  = extractMessage(xs)

      val actual = PostgresqlError.validatePacket(severity.toValidatedNel, code.toValidatedNel,
        message.toValidatedNel)(RequiredParams.apply)
      val expected = validatePacket(severity.toValidatedNel, code.toValidatedNel,
        message.toValidatedNel)(RequiredParams.apply)

      actual must_== expected
    }

    val testInvalidSqlStateCodeMessage = forAll(invalidSqlStateCodeMessageFieldsGen) { xs: Fields =>
      val severity = extractSeverity(xs)
      val code     = extractCode(xs)
      val message  = extractMessage(xs)

      val actual = PostgresqlError.validatePacket(severity.toValidatedNel, code.toValidatedNel,
        message.toValidatedNel)(RequiredParams.apply)
      val expected = validatePacket(severity.toValidatedNel, code.toValidatedNel,
        message.toValidatedNel)(RequiredParams.apply)

      actual must_== expected
    }

    val testInvalidSeverityMessage = forAll(invalidSeverityMessageFieldsGen) { xs: Fields =>
      val severity = extractSeverity(xs)
      val code     = extractCode(xs)
      val message  = extractMessage(xs)

      val actual = PostgresqlError.validatePacket(severity.toValidatedNel, code.toValidatedNel,
        message.toValidatedNel)(RequiredParams.apply)
      val expected = validatePacket(severity.toValidatedNel, code.toValidatedNel,
        message.toValidatedNel)(RequiredParams.apply)

      actual must_== expected
    }
    
    val testInvalidSeveritySqlStateCode = forAll(invalidSeveritySqlStateCodeFieldsGen) { xs: Fields =>
      val severity = extractSeverity(xs)
      val code     = extractCode(xs)
      val message  = extractMessage(xs)

      val actual = PostgresqlError.validatePacket(severity.toValidatedNel, code.toValidatedNel,
        message.toValidatedNel)(RequiredParams.apply)
      val expected = validatePacket(severity.toValidatedNel, code.toValidatedNel,
        message.toValidatedNel)(RequiredParams.apply)

      actual must_== expected
    }

    val testInvalidAll = forAll(genOptionalFields) { xs: Fields =>
      val severity = extractSeverity(xs)
      val code     = extractCode(xs)
      val message  = extractMessage(xs)

      val actual = PostgresqlError.validatePacket(severity.toValidatedNel, code.toValidatedNel,
        message.toValidatedNel)(RequiredParams.apply)
      val expected = validatePacket(severity.toValidatedNel, code.toValidatedNel,
        message.toValidatedNel)(RequiredParams.apply)

      actual must_== expected
    }

    private def extractSeverity(xs: Fields): Validated[String, String] = 
      xs.find(_._1 === Severity) match {
        case Some(x) => Valid(x._2)
        case None    => Invalid("Required Severity Level was not present.")
      }
    private def extractCode(xs: Fields): Validated[String, String] = 
      xs.find(_._1 === ErrorNoticeMessageFields.Code) match {
        case Some(x) => Valid(x._2)
        case None    => Invalid("Required SQLSTATE Code was not present.")
      }
    private def extractMessage(xs: Fields): Validated[String, String] = 
      xs.find(_._1 === Message) match {
        case Some(x) => Valid(x._2)
        case None    => Invalid("Required Message was not present.")
      }
    private def validatePacket[E : Semigroup, A, B, C, D](v1: Validated[E, A], v2: Validated[E, B],
      v3: Validated[E, C]) (f: (A, B, C) => D): Validated[E, D] = (v1, v2, v3) match {
        case (Valid(a), Valid(b), Valid(c))          => Valid(f(a,b,c))
        case (i@Invalid(_), Valid(_), Valid(_))      => i
        case (Valid(_), i@Invalid(_), Valid(_))      => i
        case (Valid(_), Valid(_), i@Invalid(_))      => i
        case (Invalid(e1), Invalid(e2), Valid(_))    => Invalid(Semigroup[E].combine(e1, e2))
        case (Invalid(e1), Valid(_), Invalid(e2))    => Invalid(Semigroup[E].combine(e1, e2))
        case (Valid(_), Invalid(e1), Invalid(e2))    => Invalid(Semigroup[E].combine(e1, e2))
        case (Invalid(e1), Invalid(e2), Invalid(e3)) => 
          Invalid(Semigroup[E].combine(e1, Semigroup[E].combine(e2, e3)))
      }
  }

  case class BPFT() extends ErrorNoticeGen {

    val testValidFields = forAll(validFieldsGen) { xs: Fields =>
      PostgresqlError.buildParamsFromTuples(xs).isRight must_== true
    }

    val testInvalidFields = forAll(invalidFieldsGen) { xs: Fields =>
      PostgresqlError.buildParamsFromTuples(xs).isLeft must_== true
    }

    val testSeverityMessage = {
      val xs = List((ErrorNoticeMessageFields.Code, "Foo"), (Message, "Bar"))
      val actual = PostgresqlError.buildParamsFromTuples(xs)
      val nel = NonEmptyList("Required Severity Level was not present.")
      actual must_== Xor.Left(new ErrorResponseDecodingFailure(nel))
    }

    val testSqlStateCodeMessage = {
      val xs = List((Severity, "Foo"), (Message, "Bar"))
      val actual = PostgresqlError.buildParamsFromTuples(xs)
      val nel = NonEmptyList("Required SQLSTATE Code was not present.")
      actual must_== Xor.Left(new ErrorResponseDecodingFailure(nel))
    }

    val testMessageMessage = {
      val xs = List((Severity, "Foo"), (ErrorNoticeMessageFields.Code, "Bar"))
      val actual = PostgresqlError.buildParamsFromTuples(xs)
      val nel = NonEmptyList("Required Message was not present.")
      actual must_== Xor.Left(new ErrorResponseDecodingFailure(nel))
    }

    val testSeveritySqlStateCodeMessage = {
      val xs = List((Message, "Foo"))
      val actual = PostgresqlError.buildParamsFromTuples(xs)
      val nel = NonEmptyList("Required Severity Level was not present.",
        "Required SQLSTATE Code was not present.")
      actual must_== Xor.Left(new ErrorResponseDecodingFailure(nel))
    }

    val testSeverityMessageMessage = {
      val xs = List((ErrorNoticeMessageFields.Code, "Foo"))
      val actual = PostgresqlError.buildParamsFromTuples(xs)
      val nel = NonEmptyList("Required Severity Level was not present.",
        "Required Message was not present.")
      actual must_== Xor.Left(new ErrorResponseDecodingFailure(nel))
    }

    val testSqlStateCodeMessageMessage = {
      val xs = List((Severity, "Foo"))
      val actual = PostgresqlError.buildParamsFromTuples(xs)
      val nel = NonEmptyList("Required SQLSTATE Code was not present.",
        "Required Message was not present.")
      actual must_== Xor.Left(new ErrorResponseDecodingFailure(nel))
    }

    val testNoRequiredFieldsFoundMessage = {
      val xs = List.empty[Field]
      val actual = PostgresqlError.buildParamsFromTuples(xs)
      val nel = NonEmptyList("Required Severity Level was not present.",
        "Required SQLSTATE Code was not present.",
        "Required Message was not present.")
      actual must_== Xor.Left(new ErrorResponseDecodingFailure(nel))
    }
  }

  case class EP() extends ErrorNoticeGen {
    val testSeverity = forAll { ep: ErrorParams =>
      val error = new UnknownError(ep)
      error.severity must_== ep.severity
    }

    val testCode = forAll { ep: ErrorParams =>
      val error = new UnknownError(ep)
      error.code must_== ep.code
    }

    val testMessage = forAll { ep: ErrorParams =>
      val error = new UnknownError(ep)
      error.message must_== ep.message
    }

    val testDetail = forAll { ep: ErrorParams =>
      val error = new UnknownError(ep)
      error.detail must_== ep.detail
    }

    val testHint = forAll { ep: ErrorParams =>
      val error = new UnknownError(ep)
      error.hint must_== ep.hint
    }

    val testPosition = forAll { ep: ErrorParams =>
      val error = new UnknownError(ep)
      error.position must_== ep.position
    }

    val testInternalPosition = forAll { ep: ErrorParams =>
      val error = new UnknownError(ep)
      error.internalPosition must_== ep.internalPosition
    }

    val testInternalQuery = forAll { ep: ErrorParams =>
      val error = new UnknownError(ep)
      error.internalQuery must_== ep.internalQuery
    }

    val testWhere = forAll { ep: ErrorParams =>
      val error = new UnknownError(ep)
      error.where must_== ep.where
    }

    val testSchemaName = forAll { ep: ErrorParams =>
      val error = new UnknownError(ep)
      error.schemaName must_== ep.schemaName
    }

    val testTableName = forAll { ep: ErrorParams =>
      val error = new UnknownError(ep)
      error.tableName must_== ep.tableName
    }

    val testColumnName = forAll { ep: ErrorParams =>
      val error = new UnknownError(ep)
      error.columnName must_== ep.columnName
    }

    val testDataTypeName = forAll { ep: ErrorParams =>
      val error = new UnknownError(ep)
      error.dataTypeName must_== ep.dataTypeName
    }

    val testConstraintName = forAll { ep: ErrorParams =>
      val error = new UnknownError(ep)
      error.constraintName must_== ep.constraintName
    }

    val testFile = forAll { ep: ErrorParams =>
      val error = new UnknownError(ep)
      error.file must_== ep.file
    }

    val testLine = forAll { ep: ErrorParams =>
      val error = new UnknownError(ep)
      error.line must_== ep.line
    }

    val testRoutine = forAll { ep: ErrorParams =>
      val error = new UnknownError(ep)
      error.routine must_== ep.routine
    }
  }
}
