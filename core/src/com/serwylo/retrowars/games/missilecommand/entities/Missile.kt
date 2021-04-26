package com.serwylo.retrowars.games.missilecommand.entities

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector2
import com.serwylo.retrowars.games.asteroids.entities.HasBoundingSphere

class FriendlyMissile(startTurret: Turret, target: Vector2): Missile(
    200f,
    Color(0.2f, 1f, 0.2f, 1f),
    Color(0.2f, 1f, 0.2f, 0.5f),
    startTurret.position.cpy().add(0f, Turret.HEIGHT),
    target
) {
    // TODO: Doesn't work when shooting downward from the top of the turret (should this even be possible?). It will just explode immediately.
    //       https://github.com/retrowars/retrowars/projects/2#card-59798055
    override fun hasReachedDestination() = position.y >= target.y
}

class EnemyMissile(start: Vector2, public val targetCity: City): Missile(
    25f,
    Color(1f, 0.2f, 0.2f, 1f),
    Color(1f, 0.2f, 0.2f, 0.5f),
    start,
    targetCity.position.cpy().add(0f, City.HEIGHT)
) {
    override fun hasReachedDestination() = position.y <= target.y
}

abstract class Missile(speed: Float, val missileColour: Color, val trailColour: Color, private val start: Vector2, val target: Vector2) {

    companion object {

        private const val SIZE = 2f
        const val POINTS = 10000

        fun renderBulk(camera: Camera, r: ShapeRenderer, missiles: List<Missile>) {
            r.projectionMatrix = camera.combined
            r.color = Color.WHITE
            r.begin(ShapeRenderer.ShapeType.Line)
            missiles.forEach {
                r.color = it.trailColour
                r.line(it.start, it.position)

                r.color = it.missileColour
                r.rect(it.position.x - SIZE / 2, it.position.y - SIZE / 2, SIZE, SIZE)
            }
            r.end()
        }

    }

    protected val position = start.cpy()
    private val velocity = target.cpy().sub(start).nor().scl(speed)

    fun update(delta: Float) {
        position.mulAdd(velocity, delta)
    }

    abstract fun hasReachedDestination(): Boolean

    fun isColliding(entity: HasBoundingSphere): Boolean {
        val distance2 = entity.getPosition().dst2(this.position)
        val collideDistance = (SIZE / 2 + entity.getRadius())

        return distance2 < collideDistance * collideDistance
    }

}