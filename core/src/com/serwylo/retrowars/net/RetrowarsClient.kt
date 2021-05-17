package com.serwylo.retrowars.net

import com.badlogic.gdx.Gdx
import com.esotericsoftware.kryonet.Client
import com.esotericsoftware.kryonet.Connection
import com.esotericsoftware.kryonet.FrameworkMessage
import com.esotericsoftware.kryonet.Listener
import com.esotericsoftware.kryonet.Listener.ThreadedListener
import com.serwylo.retrowars.net.Network.register

class RetrowarsClient {

    companion object {

        private const val TAG = "RetorwarsClient"
        private var client: RetrowarsClient? = null

        fun connect(): RetrowarsClient {
            Gdx.app.log(TAG, "Establishing connecting from client to server.")
            if (client != null) {
                throw IllegalStateException("Cannot connect to server, client connection has already been opened.")
            }

            val newClient = RetrowarsClient()
            client = newClient
            return newClient
        }

        fun get(): RetrowarsClient? = client

        fun disconnect() {
            client?.close()
            client = null
        }

    }

    val players = mutableListOf<Player>()
    val scores = mutableMapOf<Player, Long>()

    /**
     * By convention, the server always tells a client about themselves first before then passing
     * through details of all other players. Thus, the first player corresponds to the client in question.
     */
    fun me():Player? =
        if (players.size == 0) null else players[0]

    /**
     * Opposite of [me]. All players but the first.
     */
    fun otherPlayers(): List<Player> =
        if (players.size == 0) emptyList() else players.subList(1, players.size)

    private var client = Client()

    /**
     * Record if we receive a nice graceful message from the server.
     * If we *haven't* but we still get disconnected, we can show an error message to the user.
     * If we *have* shut down gracefully by the time we receive the disconnect event, then we can display a friendlier message.
     */
    private var hasShutdownGracefully = false

    private var playersChangedListener: ((List<Player>) -> Unit)? = null
    private var startGameListener: (() -> Unit)? = null
    private var scoreChangedListener: ((player: Player, score: Long) -> Unit)? = null
    private var playerStatusChangedListener: ((player: Player, status: String) -> Unit)? = null
    private var networkCloseListener: ((wasGraceful: Boolean) -> Unit)? = null

    /**
     * The only wayt o listen to network events is via this function. It ensures that no previous
     * listeners will be left dangling around in previous views, by updating every single
     * listener (potentially to null).
     *
     * The intent is to:
     *  - Call this once per screen during the initialization phase.
     *  - Use named arguments so that you can those which are unneeded.
     *
     *  All are optional except for [networkCloseListener], if you choose to interact with the
     *  client, then you need to make sure you handle disconnections properly, because we don't
     *  know when these could happen.
     */
    fun listen(
        networkCloseListener: ((wasGraceful: Boolean) -> Unit),
        playersChangedListener: ((List<Player>) -> Unit)? = null,
        startGameListener: (() -> Unit)? = null,
        scoreChangedListener: ((player: Player, score: Long) -> Unit)? = null,
        playerStatusChangedListener: ((player: Player, status: String) -> Unit)? = null
    ) {
        this.playersChangedListener = playersChangedListener
        this.startGameListener = startGameListener
        this.scoreChangedListener = scoreChangedListener
        this.playerStatusChangedListener = playerStatusChangedListener
        this.networkCloseListener = networkCloseListener
    }

    init {

        client.start()

        register(client)

        client.addListener(ThreadedListener(object : Listener {
            override fun connected(connection: Connection) {}

            override fun disconnected(connection: Connection) {
                if (hasShutdownGracefully) {
                    Gdx.app.log(TAG, "Client received disconnected event. Previously received a graceful shutdown so will broadcast that.")
                } else {
                    Gdx.app.log(TAG, "Client received disconnected event. No graceful shutdown from server, so will broadcast that.")
                }
                networkCloseListener?.invoke(hasShutdownGracefully)
            }

            override fun received(connection: Connection, obj: Any) {
                if (obj !is FrameworkMessage.KeepAlive) {
                    Gdx.app.log(TAG, "Received message from server: $obj")
                }

                when(obj) {
                    is Network.Client.OnPlayerAdded -> onPlayerAdded(obj.id, obj.game)
                    is Network.Client.OnPlayerRemoved -> onPlayerRemoved(obj.id)
                    is Network.Client.OnPlayerScored -> onScoreChanged(obj.id, obj.score)
                    is Network.Client.OnPlayerStatusChange -> onStatusChanged(obj.id, obj.status)
                    is Network.Client.OnPlayerReturnedToLobby -> onReturnedToLobby(obj.id, obj.game)
                    is Network.Client.OnStartGame -> onStartGame()
                    is Network.Client.OnServerStopped -> onServerStopped()
                }
            }
        }))

        val address = client.discoverHost(Network.defaultUdpPort, 10000)
        // TODO: Error if no server found
        client.connect(5000, address, Network.defaultPort, Network.defaultUdpPort)
        client.sendTCP(Network.Server.RegisterPlayer())

    }

    private fun onServerStopped() {
        Gdx.app.log(TAG, "Recording that server stopped somewhat-gracefully.")
        hasShutdownGracefully = true
    }

    private fun onStartGame() {
        // We reuse the same servers/clients many time over if you finish a game and immediately
        // start a new one. Therefore we need to forget all we know about peoples scores before
        // continuing with a new game.
        scores.clear()
        players.forEach { it.status = Player.Status.playing }
        startGameListener?.invoke()
    }

    private fun onPlayerAdded(id: Long, game: String) {
        players.add(Player(id, game))
        playersChangedListener?.invoke(players.toList())
    }

    private fun onPlayerRemoved(id: Long) {
        players.removeAll { it.id == id }
        playersChangedListener?.invoke(players.toList())
    }

    private fun onScoreChanged(playerId: Long, score: Long) {
        val player = players.find { it.id == playerId } ?: return

        Gdx.app.log(TAG, "Updating player $playerId score to $score")
        scores[player] = score
        scoreChangedListener?.invoke(player, score)
    }

    private fun onStatusChanged(playerId: Long, status: String) {
        val player = players.find { it.id == playerId } ?: return

        if (!Player.Status.isValid(status)) {
            Gdx.app.error(TAG, "Received unsupported status: $status... will ignore. Is this a client/server that is running the same version?")
            return
        }

        Gdx.app.log(TAG, "Received status change for player $playerId: $status")
        player.status = status
        playerStatusChangedListener?.invoke(player, status)
    }

    private fun onReturnedToLobby(playerId: Long, playersNewGame: String) {
        val player = players.find { it.id == playerId } ?: return

        // TODO: Validate game.

        Gdx.app.log(TAG, "Received return to lobby request for player $playerId. New game: $playersNewGame")
        player.status = Player.Status.lobby
        player.game = playersNewGame
        playerStatusChangedListener?.invoke(player, Player.Status.lobby)
    }

    fun changeStatus(status: String) {
        me()?.status = status
        client.sendTCP(Network.Server.UpdateStatus(status))
    }

    fun updateScore(score: Long) {
        val me = me()
        if (me != null) {
            scores[me] = score
        }
        client.sendTCP(Network.Server.UpdateScore(score))
    }

    fun close() {
        client.close()
    }

    fun getScoreFor(player: Player): Long {
        return scores[player] ?: 0
    }

}