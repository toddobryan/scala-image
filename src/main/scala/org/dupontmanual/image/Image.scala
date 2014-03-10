package org.dupontmanual.image

import scala.xml.NodeSeq
import math.ceil
import math.Pi
import java.io.File
import java.io.ByteArrayOutputStream
import org.apache.commons.codec.binary.Base64
import java.io.FileInputStream
import java.util.Arrays
import scalafx.Includes._
import scalafx.scene.Scene
import scalafx.scene.Node
import scalafx.stage.Stage
import scalafx.scene.Parent
import scalafx.scene.layout.BorderPane
import scalafx.scene.control.Button
import scalafx.scene.image.{ Image => SfxImage, ImageView, WritableImage }
import scalafx.scene.Group
import javax.imageio.ImageIO
import javafx.embed.swing.SwingFXUtils
import java.awt.image.RenderedImage
import scalafx.geometry.BoundingBox
import scalafx.geometry.Bounds
import scalafx.scene.shape.Shape
import scalafx.scene.transform.{ Transform => SfxTransform }
import scalafx.application.Platform
import scalafx.stage.StageStyle
import scalafx.geometry.Insets

/** represents an image */
abstract class Image private[image] () {
  private[image] def img: Node

  /** displays the image in a dialog box */
  def display() {
    Platform.runLater {

      // Create dialog
      val dialogStage = new Stage(StageStyle.UTILITY) {
        outer =>
        title = "Image"
        scene = new Scene {
          root = new BorderPane {
            padding = Insets(25)
            center = img
            bottom = new Button {
              text = "OK"
              onAction = handle { outer.close() }
            }
          }
        }
      }

      // Show dialog and wait till it is closed
      dialogStage.showAndWait()
      //System.exit(0)
    }
  }

  private[image] lazy val writableImg: WritableImage = new Scene {
    root = new Group(img)
  }.snapshot(null)

  private[image] lazy val savableImg: RenderedImage = SwingFXUtils.fromFXImage(writableImg, null)

  /**
   * saves the image as a `.png` file. If the filename doesn't already end
   * in `.png`, the extension is added.
   */
  def saveAsDisplayed(filename: String) {
    val filenameWithExt = if (filename.endsWith(".png")) filename else filename + ".png"
    ImageIO.write(savableImg, "png", new File(filenameWithExt))
  }

  /** returns a byte array representing the image as a PNG file */
  def bytesPng: Array[Byte] = {
    val bytes: ByteArrayOutputStream = new ByteArrayOutputStream()
    ImageIO.write(savableImg, "png", bytes)
    bytes.toByteArray
  }

  /** returns the binary value of the image as PNG image, encoded as a base64 String */
  def base64png: String = {
    Base64.encodeBase64String(bytesPng)
  }

  def inlineImgTag: NodeSeq = {
    <img src={ s"data:image/png;base64,${this.base64png}" }/>
  }

  private[image] def penWidth: Double = 0.0

  /** the actual bounds of the image (may not be rectangular) */
  /*private[image]*/ def bounds: Bounds = img.layoutBounds.value
  /** the bounding box */
  /*private[image]*/ def displayBounds: BoundingBox =
    new BoundingBox(bounds.minX, bounds.minY, bounds.width, bounds.height)

  /** returns the width of this `Image` */
  def width: Double = displayBounds.width
  /** returns the height of this `Image` */
  def height: Double = displayBounds.height

  /** returns a new `Image` with this `Image` centered and superimposed on `img` */
  def stackOn(img: Image): Image = Stack(this, img)

  /**
   * returns a new `Image` with this `Image` superimposed on `img` and aligned
   * according to `xAlign` and `yAlign`
   */
  def stackOn(img: Image, xAlign: XAlign, yAlign: YAlign): Image = Stack(this, img, xAlign, yAlign)

  /**
   * returns a new `Image` with this `Image` superimposed on `img` and moved
   * `dx` pixels to the right and `dy` pixels down from center. `dx` and
   * `dy` may be negative to translate this `Image` to the left and up.
   */
  def stackOn(img: Image, dx: Double, dy: Double): Image = Stack(this, img, dx, dy)

  /**
   * returns a new `Image` with this `Image` superimposed on `img`, aligned
   * according to `xAlign` and `yAlign` and translated `dx` pixels to
   * the right and `dy` pixels down.
   */
  def stackOn(img: Image, xAlign: XAlign, yAlign: YAlign, dx: Double, dy: Double): Image =
    Stack(this, img, xAlign, yAlign, dx, dy)

  /** equivalent to `img.stackOn(this)` */
  def slideUnder(img: Image): Image = Stack(img, this)
  /** equivalent to `img.stackOn(this, xAlign, yAlign)` */
  def slideUnder(img: Image, xAlign: XAlign, yAlign: YAlign): Image = Stack(img, this, xAlign, yAlign)
  /** equivalent to `img.stackOn(this, dx, dy)` */
  def slideUnder(img: Image, dx: Double, dy: Double): Image = Stack(img, this, dx, dy)
  /** equivalent to `img.stackOn(this, xAlign, yAlign, dx, dy)` */
  def slideUnder(img: Image, xAlign: XAlign, yAlign: YAlign, dx: Double, dy: Double): Image =
    Stack(img, this, xAlign, yAlign, dx, dy)

  /**
   * produces a new `Image` by placing `img` on this `Image`. `x` and `y` represent how far
   *  to the right and down the `img` is placed. The final image is restricted to the portion
   *  that would have shown on this `Image`, so you're guaranteed that the new `Image` created
   *  will be exactly the same size as this `Image`. The center of `img` is placed at the point
   *  (`x`, `y`) on the new `Image`.
   */
  def placeImage(img: Image, x: Double, y: Double): Image = this.placeImage(img, x, y, XAlign.Center, YAlign.Center)
  /**
   * produces a new `Image` by placing `img` on this `Image`. `x` and `y` represent how far
   *  to the right and down the point designated by `xAlign` and `yAlign` are offset from the
   *  top left of this `Image`. Any portion of `img` that would extend outside the bounds of
   *  this `Image` is not retained, so you're guaranteed that the new `Image` will be exactly
   *  the same size as this `Image`.
   */
  def placeImage(img: Image, x: Double, y: Double, xAlign: XAlign, yAlign: YAlign): Image = {
    val xOffset: Double = img.width * (xAlign match {
      case XAlign.Left => 0.0
      case XAlign.Center => -0.5
      case XAlign.Right => -1.0
    })
    val yOffset: Double = img.height * (yAlign match {
      case YAlign.Top => 0.0
      case YAlign.Center => -0.5
      case YAlign.Bottom => -1.0
    })
    val scene = Stack(img, this, XAlign.Left, YAlign.Top, x + xOffset, y + yOffset)
    // check to see if img even appears in the new scene; if not, we'll return the old one
    val bg = scene.backBounds
    if (scene.frontBounds.intersects(bg)) {
      val btl = scene.backTopLeft
      scene.crop(btl.x, btl.y, bg.getWidth, bg.getHeight)
    } else {
      this
    }
  }

  /** produces a new `Image` with `img` to the right of this `Image`, centered vertically */
  def beside(img: Image): Image = this.beside(img, YAlign.Center)
  /** produces a new `Image` with `img` to the right of this `Image`, aligned according to `yAlign` */
  def beside(img: Image, yAlign: YAlign): Image = Stack(img, this, XAlign.Left, yAlign, this.width, 0)

  /** produces a new `Image` with `img` vertically below this `Image`, centered horizontally */
  def above(img: Image): Image = this.above(img, XAlign.Center)
  /** produces a new `Image` with `img` vertically below this `Image`, aligned according to `xAlign` */
  def above(img: Image, xAlign: XAlign): Image = Stack(img, this, xAlign, YAlign.Top, 0, this.height)

  /** produces the `Image` obtained by reflecting this `Image` left to right */
  def flipHorizontal(): Image =
    new Transform(this, List(SfxTransform.translate(width, 0), SfxTransform.scale(-1, 1)))

  /** produces the `Image` obtained by reflecting this `Image` top to bottom */
  def flipVertical(): Image =
    new Transform(this, List(SfxTransform.translate(0, height), SfxTransform.scale(1, -1)))

  /**
   * produces the `Image` obtained by scaling this `Image` horizontally
   * by `xFactor` and vertically by `yFactor`
   */
  def scale(xFactor: Double, yFactor: Double): Image =
    new Transform(this, List(SfxTransform.scale(xFactor, yFactor)))

  /** produces the `Image` obtained by scaling this `Image` horizontally by `xFactor` */
  def scaleX(xFactor: Double): Image = scale(xFactor, 1.0)
  /** produces the `Image` obtained by scaling this `Image` vertically by `yFactor` */
  def scaleY(yFactor: Double): Image = scale(1.0, yFactor)

  /**
   * produces the `Image` obtained by rotating this `Image` by the angle `factor`
   * around the point (`pivotX`, `pivotY`)
   */
  def rotate(factor: Angle, pivotX: Double, pivotY: Double): Image =
    new Transform(this, List(SfxTransform.rotate(factor.toDegrees.magnitude, pivotX, pivotY)))
  /**
   * produces the `Image` obtained by rotating this `Image` by the angle `factor`
   * around its center
   */
  def rotate(factor: Angle): Image = {
    val pivotX = (bounds.minX + bounds.maxX) / 2.0
    val pivotY = (bounds.minY + bounds.maxY) / 2.0
    new Transform(this, List(SfxTransform.rotate(factor.toDegrees.magnitude, pivotX, pivotY)))
  }

  /**
   * produces the `Image` obtained by moving this `Image` `x` pixels right
   * and `y` pixels down. (`x` and `y` may be negative.)
   */
  def translate(x: Double, y: Double): Image =
    new Transform(this, List(SfxTransform.translate(x, y)))

  /**
   * produces the `Image` obtained by cutting a rectangle `cropWidth` pixels
   * wide and `cropHeight` pixels tall out of this `Image` starting at a
   * point `x` pixels to the right of and `y` pixels down from the top left
   * corner
   */
  def crop(x: Double, y: Double, cropWidth: Double, cropHeight: Double): Image = {
    new Cropped(this, x, y, cropWidth, cropHeight)
  }
  /** produces the `Image` obtained by cutting `numPixels` off the left side of this `Image` */
  def cropLeft(numPixels: Double): Image = {
    require(numPixels >= 0 && numPixels <= width, s"numPixels must be a non-negative number less than the width of this image ($width).")
    this.crop(numPixels, 0, width - numPixels, height)
  }
  /** produces the `Image` obtained by cutting `numPixels` off the right side of this `Image` */
  def cropRight(numPixels: Double): Image = {
    require(numPixels >= 0 && numPixels <= width, s"numPixels must be a non-negative number less than the width of this image ($width).")
    this.crop(0, 0, width - numPixels, height)
  }
  /** produces the `Image` obtained by cutting `numPixels` off the top side of this `Image` */
  def cropTop(numPixels: Double): Image = {
    require(numPixels >= 0 && numPixels <= height, s"numPixels must be a non-negative number less than the height of this image ($height).")
    this.crop(0, numPixels, width, height - numPixels)
  }
  /** produces the `Image` obtained by cutting `numPixels` off the bottom side of this `Image` */
  def cropBottom(numPixels: Double): Image = {
    require(numPixels >= 0 && numPixels <= height, s"numPixels must be a non-negative number less than the height of this image ($height).")
    this.crop(0, 0, width, height - numPixels)
  }

  /** produces whether this `Image` is bitmap-identical to `other` */
  def sameBitmapAs(other: Image): Boolean = {
    Arrays.equals(this.bytesPng, other.bytesPng)
  }
}


