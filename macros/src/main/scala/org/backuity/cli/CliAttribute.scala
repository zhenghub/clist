package org.backuity.cli

sealed abstract class CliAttribute[T] {
  val tpe: Class[T]
  val name: String

  /**
   * Name of the command attribute that will receive the value.
   */
  val commandAttributeName: String

  val description: Option[String]
  val reader: Read[T]
}

/**
 * An argument is a value directly passed to a command. It may be optional.
 */
abstract class CliArgument[T] extends CliAttribute[T]

case class CliOptionalArgument[T](tpe: Class[T],
                                  commandAttributeName: String,
                                  name: String,
                                  description: Option[String],
                                  default: T)
                                 (val reader: Read[T]) extends CliArgument[T] {
}

case class CliMandatoryArgument[T](tpe: Class[T],
                                  commandAttributeName: String,
                                  name: String,
                                  description: Option[String])
                                  (val reader: Read[T]) extends CliArgument[T] {
}

/**
 * As opposed to an argument an option is always optional.
 */
case class CliOption[T](tpe: Class[T],
                        commandAttributeName: String,
                        longName: Option[String],
                        description: Option[String],
                        abbrev: Option[String],
                        default: T)(val reader: Read[T]) extends CliAttribute[T] {

  val name = longName.getOrElse(abbrev.get)
}

object CliAttribute {
  
  class Builder[T: Read : Manifest](varName: String) {

    val clazz: Class[_] = manifest[T].runtimeClass

    def fail(msg: String) {
      throw new IllegalArgumentException(s"Incorrect argument $varName: $msg")
    }

    def validateDefault(default: T) : T = {

      // TODO check those at compile time (when SI-5920 gets fixed)

      if( default == null
          && clazz != classOf[Option[_]]
          && clazz != classOf[Boolean] ) {
              
        fail("an optional argument that has neither type Option nor Boolean must have a default value")
      }

      // TODO we really want to forbid setting the default value but we have no way to check that..
      //      ..it seems that `default: T = null.asInstanceOf[T]` does not get a null for primitive types
      //      but rather a default value.
      if( clazz == classOf[Boolean] &&
          default.asInstanceOf[Boolean] == true ) { // use `== true` to make that statement more readable
        fail(s"a boolean argument cannot have a default value set to true")
      }

      (if( default != null ) {
        default
      } else if( clazz == classOf[Option[_]] ) {
        None
      } else if( clazz == classOf[Boolean] ) {
        false
      }).asInstanceOf[T]
    }
  }
  

}

object CliOption {
  // this class cannot be within Cli due to some macros restrictions
  class Builder[T : Read : Manifest](command: Command, varName: String) extends CliAttribute.Builder[T](varName) {

    /**
     * - unless it is a boolean, an optional argument must have a default value
     * - a boolean cannot have a default value (we want to avoid a boolean being true
     *   by default.. it would always be true)
     */
    def apply[U <: T](name: String = null,
                      description: String = null,
                      abbrev: String = null,
                      abbrevOnly: String = null,
                      default: T = null.asInstanceOf[T]) : T = {

      // TODO check those at compile time (when SI-5920 gets fixed)

      if( abbrevOnly != null && abbrev != null ) {
        fail("cannot define both abbrev and abbrevOnly")
      }

      val nonNullDefault = validateDefault(default)

      command.addOption(CliOption(
        tpe         = manifest[T].runtimeClass.asInstanceOf[Class[T]],
        commandAttributeName      = varName,
        longName    = Option(name).orElse(Some(varName.trim)),
        description = Option(description),
        abbrev      = Option(abbrevOnly).orElse(Option(abbrev)),
        default     = nonNullDefault)(implicitly[Read[T]]))

      default
    }
  }
}

object CliArgument {
  class Builder[T : Read : Manifest](command: Command, varName: String) extends CliAttribute.Builder[T](varName) {

    /**
     * - unless it is a boolean, an optional argument must have a default value
     * - a boolean cannot have a default value (we want to avoid a boolean being true
     *   by default.. it would always be true)
     */
    def apply[U <: T](name: String = null,
                      description: String = null,
                      required: Boolean = true,
                      default: T = null.asInstanceOf[T]) : T = {

      // TODO check those at compile time (when SI-5920 gets fixed)

      if( required ) {

        if( default != null && clazz != classOf[Boolean] ) {
          fail("a required argument will ignore its default value")
        }

        command.addArgument(CliMandatoryArgument(
          tpe                  = manifest[T].runtimeClass.asInstanceOf[Class[T]],
          commandAttributeName = varName,
          name                 = Option(name).getOrElse(varName.trim),
          description          = Option(description))(implicitly[Read[T]]))
      } else {

        val nonNullDefault = validateDefault(default)

        command.addArgument(CliOptionalArgument(
          tpe                  = manifest[T].runtimeClass.asInstanceOf[Class[T]],
          commandAttributeName = varName,
          name                 = Option(name).getOrElse(varName.trim),
          description          = Option(description),
          default              = nonNullDefault)(implicitly[Read[T]]))
      }

      default
    }
  }
}