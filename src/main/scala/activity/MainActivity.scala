package me.elrod.tryidrisapp

import android.app.{ Activity, Fragment }
import android.content.{ Context, Intent }
import android.graphics.Color
import android.os.Bundle
import android.text.{ Spanned, Spannable, SpannableString }
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.view.KeyEvent
import android.view.View
import android.view.View.OnKeyListener
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.TextView.OnEditorActionListener

import argonaut._, Argonaut._
import me.elrod.tryidris._, TryIdris._

import scalaz._, Scalaz._
import scalaz.concurrent.Promise
import scalaz.concurrent.Promise._
import scalaz.effect.IO

import scala.language.implicitConversions // lolscala

class MainActivity extends Activity with TypedViewHolder {
  implicit def toRunnable[F](f: => F): Runnable = new Runnable() {
    def run(): Unit = {
      f
      ()
    }
  }

  override def onPostCreate(bundle: Bundle): Unit = {
    super.onPostCreate(bundle)
    setContentView(R.layout.main_activity)
    val output = findView(TR.output)
    val input  = findView(TR.input_code)

    def prompt = {
      val p = new SpannableString("idris> ")
      p.setSpan(
        new ForegroundColorSpan(Color.parseColor("#6D0839")),
        0,
        p.length,
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
      p
    }

    def colorize(r: InterpretResponse): IO[Option[SpannableString]] = IO {
      r.tokens match {
        //case class Token(startChar: Int, length: Int, metadata: List[(String, String)])
        case None => None // Well then.
        case Some(tokens) => {
          val s = new SpannableString(r.result)
          tokens.foreach { t =>
            val style = t.metadata.find(_._1 == ":decor").map(_._2 substring 1)
            val color = style match {
              case Some("name")  => Color.parseColor("#00ff00")
              case Some("bound") => Color.parseColor("#ff00ff")
              case Some("data")  => Color.parseColor("#ff0000")
              case Some("type")  => Color.parseColor("#0000ff")
              case Some("error") => Color.parseColor("#ff0000")
              case _             => Color.parseColor("#555555")
            }
            s.setSpan(
              new ForegroundColorSpan(color),
              t.startChar,
              t.startChar + t.length,
              Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
          }
          Some(s)
        }
      }
    }

    input.setOnEditorActionListener(new OnEditorActionListener() {
      def onEditorAction(v: TextView, actionId: Int, event: KeyEvent): Boolean = {
        if (actionId == EditorInfo.IME_ACTION_SEND) {
          output.append(prompt)
          output.append(input.getText.toString + "\n")
          promise {
            interpretIO(InterpretRequest(input.getText.toString))
              .map(toUtf8String)
              .map(_.decodeOption[InterpretResponse])
              .unsafePerformIO
            } map {
              case Some(x) => {
                val colored = colorize(x) map {
                  case Some(colored) => runOnUiThread(output.append(colored))
                  case None => // :(
                }
                colored.unsafePerformIO // TODO: unsafePerformIO :(
                runOnUiThread(output.append("\n"))
              }
              case None => {
                runOnUiThread(output.append("<ERROR!> :(\n"))// Something bad happened. :'(
              }
            }
            input.setText("")
            true
          } else {
            false
        }
      }
    })
  }
}
