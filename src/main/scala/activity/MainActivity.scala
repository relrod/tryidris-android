package me.elrod.tryidrisapp

import android.app.{ Activity, AlertDialog, Fragment }
import android.content.{ Context, Intent }
import android.graphics.Color
import android.os.Bundle
import android.text.{ Spanned, Spannable, SpannableString }
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.view.KeyEvent
import android.view.{ Menu, MenuInflater, MenuItem, View, Window }
import android.view.View.OnKeyListener
import android.widget.{ ArrayAdapter, ProgressBar, TextView, ScrollView }
import android.widget.TextView.OnEditorActionListener

import argonaut._, Argonaut._
import me.elrod.tryidris._, TryIdris._

import scalaz._, Scalaz._
import scalaz.concurrent.Promise
import scalaz.concurrent.Promise._
import scalaz.effect.IO

import scala.language.implicitConversions // lolscala

object Implicits {
  implicit def toRunnable[F](f: => F): Runnable = new Runnable() {
    def run(): Unit = {
      f
      ()
    }
  }
}

import Implicits._

class MainActivity extends Activity with TypedViewHolder {
  override def onPostCreate(bundle: Bundle): Unit = {
    super.onPostCreate(bundle)
    requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS)
    setContentView(R.layout.main_activity)
    // TODO: These should probably be Option.
    val output = findView(TR.output)
    val input  = findView(TR.input_code)
    val scrollView = Option(findView(TR.mainScrollView))
    input.setOnEditorActionListener(new IdrisOnEditorActionListener(this, output, input, scrollView))
  }

  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    val inflater: MenuInflater = getMenuInflater
    inflater.inflate(R.menu.options, menu);
    true
  }

  override def onOptionsItemSelected(item: MenuItem): Boolean = {
    item.getItemId match {
      case R.id.about => {
        val b = new AlertDialog.Builder(this)
          .setTitle("About Try Idris")
          .setMessage("Try Idris Android App (c) 2014 Ricky Elrod. Powered by the awesome http://tryidris.org/ by Brian McKenna.")
          .show
      }
      case _ => ()
    }
    true
  }
}

class IdrisOnEditorActionListener(
  c: Activity,
  output: TextView,
  input: TextView,
  scrollView: Option[ScrollView]) extends OnEditorActionListener {
  private def prompt = {
    val p = new SpannableString("idris> ")
    p.setSpan(
      new ForegroundColorSpan(Color.parseColor("#6D0839")),
      0,
      p.length,
      Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    p
  }

  def onEditorAction(v: TextView, actionId: Int, event: KeyEvent): Boolean =
    if (actionId == EditorInfo.IME_ACTION_SEND) {
      // This feels weird, but it is the best I know how to do right now.
      runTheWorld(v, actionId, event).map(_.unsafePerformIO)
      true
    } else {
      false
    }

  def runTheWorld(v: TextView, actionId: Int, event: KeyEvent): Promise[IO[Unit]] = {
    promise {
      for {
        _ <- IO { c.runOnUiThread(c.setProgressBarIndeterminateVisibility(true)) }
        resp <- interpretIO(InterpretRequest(input.getText.toString))
             .map(toUtf8String)
             .map(_.decodeOption[InterpretResponse])
      } yield (resp)
    } map { ioo =>
      ioo.flatMap { res =>
        IO {
          c.runOnUiThread(output.append(prompt))
          c.runOnUiThread(output.append(input.getText.toString + "\n"))
          c.runOnUiThread(input.setText(""))
          res match {
            case Some(x) => {
              colorize(x) match {
                case Some(colored) => c.runOnUiThread(output.append(colored))
                case None => c.runOnUiThread(output.append(x.result))
              }
              c.runOnUiThread(output.append("\n"))
            }
            case None => {
              c.runOnUiThread(output.append("<ERROR!> :(\n"))// Something bad happened. :'(
            }
          }
          scrollView.map(_.fullScroll(View.FOCUS_DOWN))
          c.runOnUiThread(c.setProgressBarIndeterminateVisibility(false))
          ()
        }
      }
    }
  }

  def colorize(r: InterpretResponse): Option[SpannableString] =
    r.tokens match {
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
