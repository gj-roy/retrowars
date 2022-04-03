package com.serwylo.retrowars.core

import com.badlogic.gdx.*
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.VerticalGroup
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.Align
import com.serwylo.beatgame.ui.*
import com.serwylo.retrowars.RetrowarsGame
import com.serwylo.retrowars.utils.Options

class MainMenuScreen(private val game: RetrowarsGame): ScreenAdapter() {

    private val stage = makeStage()

    init {
        val styles = game.uiAssets.getStyles()
        val strings = game.uiAssets.getStrings()

        val container = VerticalGroup().apply {
            setFillParent(true)
            align(Align.center)
            space(UI_SPACE)
        }

        container.addActor(
            makeHeading(
                game.uiAssets.getSprites().icons.retrowars,
                strings["app.name"],
                styles,
                strings
            )
        )

        container.addActor(Table().apply {
            add(
                makeLargeButton(strings["main-menu.btn.play-single-player"], styles) {
                    game.showGameSelectMenu()
                }
            ).fillX()

            row()
            add(
                makeLargeButton(strings["main-menu.btn.play-multiplayer"], styles) {
                    game.showMultiplayerLobby()
                }
            ).fillX()

            row()
            add(
                makeButton(strings["main-menu.btn.options"], styles) {
                    game.showOptions()
                }
            ).fillX()


            if (Gdx.app.type == Application.ApplicationType.Desktop) {
                row()
                add(
                    makeButton(strings["main-menu.btn.quit"], styles) {
                        Gdx.app.exit()
                    }
                ).fillX()
            }
        })

        stage.addActor(container)

        makeToggleAudioButton(game.uiAssets.getSprites()) { mute ->
            setupMusicState(mute)
        }.also { btn ->
            btn.x = UI_SPACE * 2
            btn.y = UI_SPACE * 2
            stage.addActor(btn)
        }

        setupMusicState(Options.isMute())

    }

    private fun setupMusicState(mute: Boolean) {
        if (!mute) {
            game.unmute()
        } else {
            game.mute()
        }
    }

    override fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
        game.uiAssets.getEffects().resize(width, height)
    }

    override fun show() {
        Gdx.input.inputProcessor = stage
    }

    override fun hide() {
        Gdx.input.inputProcessor = null
    }

    override fun render(delta: Float) {
        stage.act(delta)

        game.uiAssets.getEffects().render {
            stage.draw()
        }
    }

}
