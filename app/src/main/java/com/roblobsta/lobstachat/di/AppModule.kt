package com.roblobsta.lobstachat.di

import android.content.Context
import android.graphics.Color
import android.text.util.Linkify
import android.util.TypedValue
import androidx.core.content.res.ResourcesCompat
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.CorePlugin
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import io.noties.markwon.syntax.Prism4jThemeDarkula
import io.noties.markwon.syntax.SyntaxHighlightPlugin
import io.noties.prism4j.Prism4j
import com.roblobsta.lobstachat.R
import com.roblobsta.lobstachat.prism4j.PrismGrammarLocator
import org.koin.dsl.module

val appModule = module {
    single {
        val context = get<Context>()
        val prism4j = Prism4j(PrismGrammarLocator())
        Markwon.builder(context)
            .usePlugin(CorePlugin.create())
            .usePlugin(SyntaxHighlightPlugin.create(prism4j, Prism4jThemeDarkula.create()))
            .usePlugin(MarkwonInlineParserPlugin.create())
            .usePlugin(
                JLatexMathPlugin.create(
                    12f,
                    JLatexMathPlugin.BuilderConfigure {
                        it.inlinesEnabled(true)
                        it.blocksEnabled(true)
                    },
                ),
            ).usePlugin(LinkifyPlugin.create(Linkify.WEB_URLS))
            .usePlugin(HtmlPlugin.create())
            .usePlugin(
                object : AbstractMarkwonPlugin() {
                    override fun configureTheme(builder: MarkwonTheme.Builder) {
                        val jetbrainsMonoFont =
                            ResourcesCompat.getFont(context, R.font.jetbrains_mono)!!
                        builder
                            .codeBlockTypeface(
                                ResourcesCompat.getFont(context, R.font.jetbrains_mono)!!,
                            ).codeBlockTextColor(Color.WHITE)
                            .codeBlockTextSize(spToPx(context,10f))
                            .codeBlockBackgroundColor(Color.BLACK)
                            .codeTypeface(jetbrainsMonoFont)
                            .codeTextSize(spToPx(context,10f))
                            .codeTextColor(Color.WHITE)
                            .codeBackgroundColor(Color.BLACK)
                            .isLinkUnderlined(true)
                    }
                },
            ).build()
    }
}

private fun spToPx(context: Context, sp: Float): Int =
    TypedValue
        .applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, context.resources.displayMetrics)
        .toInt()
