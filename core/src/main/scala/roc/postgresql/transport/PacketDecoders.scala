package roc
package postgresql
package transport

import cats.implicits._
import roc.postgresql.failures.{Failure, PacketDecodingFailure}
import roc.postgresql.server.PostgresqlMessage

private[postgresql] trait PacketDecoder[A <: BackendMessage] {
  def apply(p: Packet): PacketDecoder.Result[A]
}
private[postgresql] object PacketDecoder {
  final type Result[A] = Either[Failure, A]
}

private[postgresql] trait PacketDecoderImplicits {
  import PacketDecoder._

  private[this] type Field = (Char, String)
  private[this] type Fields = List[Field]

  def readErrorNoticePacket(p: Packet): Either[Throwable, Fields] = Either.catchNonFatal({
    val br = BufferReader(p.body)

    @annotation.tailrec
    def loop(xs: List[Field]): List[Field]= br.readByte match {
      case 0x00 => xs // null
      case byte => {
        val field = (byte.toChar, br.readNullTerminatedString())
        loop(field :: xs)
      }
    }
    loop(List.empty[Field])
  })

  implicit val noticeResponsePacketDecoder: PacketDecoder[NoticeResponse] =
    new PacketDecoder[NoticeResponse] {
      def apply(p: Packet): Result[NoticeResponse] = readErrorNoticePacket(p)
        .leftMap(t => new PacketDecodingFailure(t.getMessage))
        .flatMap(xs => PostgresqlMessage(xs).fold(
          {l => Left(l)},
          {r => Right(new NoticeResponse(r))}
        ))
    }

  implicit val errorMessagePacketDecoder: PacketDecoder[ErrorResponse] =
    new PacketDecoder[ErrorResponse] {
      def apply(p: Packet): Result[ErrorResponse] = readErrorNoticePacket(p)
      .leftMap(t => new PacketDecodingFailure(t.getMessage))
      .flatMap(xs => PostgresqlMessage(xs).fold(
        {l => Left(l)},
        {r => Right(new ErrorResponse(r))}
      ))
    }

  implicit val commandCompletePacketDecoder: PacketDecoder[CommandComplete] =
    new PacketDecoder[CommandComplete] {
      def apply(p: Packet): Result[CommandComplete] = Either.catchNonFatal({
        val br = BufferReader(p.body)
        val commandTag = br.readNullTerminatedString()
        new CommandComplete(commandTag)
      }).leftMap(t => new PacketDecodingFailure(t.getMessage))
    }

  implicit val parameterStatusPacketDecoder: PacketDecoder[ParameterStatus] =
    new PacketDecoder[ParameterStatus] {
      def apply(p: Packet): Result[ParameterStatus] = Either.catchNonFatal({
        val br = BufferReader(p.body)
        val param = br.readNullTerminatedString()
        val value = br.readNullTerminatedString()
        new ParameterStatus(param, value)
      }).leftMap(t => new PacketDecodingFailure(t.getMessage))
    }

  implicit val backendKeyDataPacketDecoder: PacketDecoder[BackendKeyData] =
    new PacketDecoder[BackendKeyData] {
      def apply(p: Packet): Result[BackendKeyData] = Either.catchNonFatal({
        val br        = BufferReader(p.body)
        val processId = br.readInt
        val secretKey = br.readInt
        new BackendKeyData(processId, secretKey)
      }).leftMap(t => new PacketDecodingFailure(t.getMessage))
    }

  implicit val readyForQueryPacketDecoder: PacketDecoder[ReadyForQuery] =
    new PacketDecoder[ReadyForQuery] {
      def apply(p: Packet): Result[ReadyForQuery] = Either.catchNonFatal({
        val br   = BufferReader(p.body)
        val byte = br.readByte
        byte.toChar
      })
      .leftMap(t => new PacketDecodingFailure(t.getMessage))
      .flatMap(ReadyForQuery(_))
    }

  implicit val rowDescriptionPacketDecoder: PacketDecoder[RowDescription] =
    new PacketDecoder[RowDescription] {
      def apply(p: Packet): Result[RowDescription] = Either.catchNonFatal({
        val br        = BufferReader(p.body)
        val numFields = br.readShort

        @annotation.tailrec
        def loop(currCount: Short, fs: List[RowDescriptionField]): List[RowDescriptionField] =
          currCount match {
            case x if x < numFields => {
              val name = br.readNullTerminatedString()
              val tableObjectId = br.readInt
              val tableAttributeId = br.readShort
              val dataTypeObjectId = br.readInt
              val dataTypeSize = br.readShort
              val typeModifier = br.readInt
              val formatCode = br.readShort match {
                case 0 => TextFormat
                case 1 => BinaryFormat
                case s => throw new Exception(s"Unknown format code $s.")
              }

              val rdf = RowDescriptionField(name, tableObjectId, tableAttributeId, dataTypeObjectId,
                dataTypeSize, typeModifier, formatCode)
              loop((currCount + 1).toShort, rdf :: fs)
            }
            case x if x >= numFields => fs.reverse
          }

        val fs = loop(0, List.empty[RowDescriptionField])
        RowDescription(numFields, fs)
      }).leftMap(t => new PacketDecodingFailure(t.getMessage))
    }

    implicit val dataRowPacketDecoder: PacketDecoder[DataRow] = new PacketDecoder[DataRow] {
      def apply(p: Packet): Result[DataRow] = Either.catchNonFatal({
        val br = BufferReader(p.body)
        val columns = br.readShort

        @annotation.tailrec
        def loop(idx: Short, cbs: List[Option[Array[Byte]]]): List[Option[Array[Byte]]] =
          idx match {
            case x if x < columns => {
              val columnLength = br.readInt
              val bytes = if(columnLength == -1) {
                None
              } else if(columnLength == 0) {
                Some(Array.empty[Byte])
              } else {
                Some(br.take(columnLength))
              }
              loop((idx + 1).toShort, bytes :: cbs)
            }
            case x if x >= columns => cbs.reverse
          }

        val columnBytes = loop(0, List.empty[Option[Array[Byte]]])
        new DataRow(columns, columnBytes)
      }).leftMap(t => new PacketDecodingFailure(t.getMessage))
    }

  implicit val authenticationMessagePacketDecoder: PacketDecoder[AuthenticationMessage] = 
    new PacketDecoder[AuthenticationMessage] {
      def apply(p: Packet): Result[AuthenticationMessage] = Either.catchNonFatal({
        val br = BufferReader(p.body)
        br.readInt match {
          case 0 => (0, None)
          case 2 => (2, None)
          case 3 => (3, None)
          case 5 => (5, Some(br.take(4)))
          case 6 => (6, None)
          case 7 => (7, None)
          case 8 => (8, Some(br.takeRest))
          case 9 => (9, None)
          case n => (n, None)
        }
      })
      .leftMap(t => new PacketDecodingFailure(t.getMessage))
      .flatMap(AuthenticationMessage(_))
    }
}
