package rat.poison.scripts.esp

import com.badlogic.gdx.graphics.Color
import com.sun.jna.Memory
import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap
import org.jire.arrowhead.unsign
import rat.poison.App
import rat.poison.curSettings
import rat.poison.game.CSGO.csgoEXE
import rat.poison.game.entity.*
import rat.poison.game.entity.EntityType.Companion.ccsPlayer
import rat.poison.game.forEntities
import rat.poison.game.me
import rat.poison.game.worldToScreen
import rat.poison.settings.DANGER_ZONE
import rat.poison.settings.MENUTOG
import rat.poison.strToBool
import rat.poison.utils.Vector
import rat.poison.utils.collections.CacheableList
import rat.poison.utils.extensions.uint
import rat.poison.utils.notInGame

private val bones = Array(2048) { Line() }
private val entityBones = Long2ObjectArrayMap<CacheableList<Pair<Int, Int>>>()
private var currentIdx = 0

internal fun skeletonEsp() = App {
	if (!curSettings["SKELETON_ESP"].strToBool() || !curSettings["ENABLE_ESP"].strToBool() || MENUTOG || notInGame) return@App

	val meTeam = me.team()
	forEntities(ccsPlayer) {
		val entity = it.entity
		val entTeam = entity.team()

		val dormCheck = (entity.dormant() && !DANGER_ZONE)
		val enemyCheck = ((!curSettings["SKELETON_SHOW_ENEMIES"].strToBool() && meTeam != entTeam) && !DANGER_ZONE)
		val teamCheck = ((!curSettings["SKELETON_SHOW_TEAM"].strToBool() && meTeam == entTeam) && !DANGER_ZONE)

		if (entity == me || entity.dead() || dormCheck || enemyCheck || teamCheck) return@forEntities false
		(entityBones.get(entity) ?: CacheableList(20)).apply {
			if (isEmpty()) {
				val studioModel = csgoEXE.uint(entity.studioHdr())
				val numBones = csgoEXE.uint(studioModel + 0x9C).toInt()
				val boneOffset = csgoEXE.uint(studioModel + 0xA0)

				val modelMemory: Memory by lazy {
					Memory(21332)
				}

				csgoEXE.read(studioModel + boneOffset, modelMemory)

				var offset = 0
				for (idx in 0 until numBones) {
					val parent = csgoEXE.int(studioModel + boneOffset + 0x4 + offset)
					if (parent != -1) {
						val flags = modelMemory.getInt(0xA0L + offset).unsign() and 0x100
						if (flags != 0L) add(parent to idx)
					}

					offset += 216
				}

				entityBones[entity] = this
			}

			forEach { et -> drawBone(entity, et.first, et.second); false }
		}

		false
	}

	shapeRenderer.apply {
		if (isDrawing) {
			end()
		}

		begin()
		for (i in 0 until currentIdx) {
			val bone = bones[i]
			color = bone.color
			line(bone.sX.toFloat(), bone.sY.toFloat(), bone.eX.toFloat(), bone.eY.toFloat())
		}
		end()
	}

	currentIdx = 0
}

private val colors: Array<Color> = Array(101) {
	val red = 1 - (it / 100f)
	val green = (it / 100f)

	Color(red, green, 0f, 1f)
}

private val startBone = Vector()
private val endBone = Vector()

private val startDraw = Vector()
private val endDraw = Vector()

private fun drawBone(target: Player, start: Int, end: Int) {
	//Reduce r/w
	//Replace later
	val boneMemory: Memory by lazy {
		Memory(4032)
	}

	csgoEXE.read(target.boneMatrix(), boneMemory)

	startBone.set(
			boneMemory.getFloat(((0x30L * start) + 0xC)).toDouble(),
			boneMemory.getFloat(((0x30L * start) + 0x1C)).toDouble(),
			boneMemory.getFloat(((0x30L * start) + 0x2C)).toDouble())
	endBone.set(
			boneMemory.getFloat(((0x30L * end) + 0xC)).toDouble(),
			boneMemory.getFloat(((0x30L * end) + 0x1C)).toDouble(),
			boneMemory.getFloat(((0x30L * end) + 0x2C)).toDouble())

	if (worldToScreen(startBone, startDraw) && worldToScreen(endBone, endDraw)) {
		bones[currentIdx].apply {
			sX = startDraw.x.toInt()
			sY = startDraw.y.toInt()
			eX = endDraw.x.toInt()
			eY = endDraw.y.toInt()
			val health = target.health()
			if (health >= 0 && health < colors.size) {
				color = colors[health]
			}
		}
		currentIdx++
	}
}

private data class Line(var sX: Int = -1, var sY: Int = -1,
						var eX: Int = -1, var eY: Int = -1,
						var color: Color = Color.WHITE)