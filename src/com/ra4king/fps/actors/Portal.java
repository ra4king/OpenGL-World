package com.ra4king.fps.actors;

import com.ra4king.fps.Camera;
import com.ra4king.fps.OpenGLWorlds;
import com.ra4king.fps.world.World;
import com.ra4king.opengl.util.math.Quaternion;
import com.ra4king.opengl.util.math.Vector2;
import com.ra4king.opengl.util.math.Vector3;

import net.indiespot.struct.cp.Struct;
import net.indiespot.struct.cp.TakeStruct;

/**
 * @author Roi Atalla
 */
public class Portal implements Actor {
	private OpenGLWorlds worldsManager;
	
	private World parentWorld;
	private Vector3 position;
	private Vector2 size;
	private Quaternion orientation;
	
	private World destWorld;
	private Portal destPortal;
	
	/**
	 * Without orientation, the portal is by default on the XY plane with the position as the top left corner of the quad.
	 */
	public Portal(OpenGLWorlds worldsManager, World parentWorld, Vector3 position, Vector2 size, Quaternion orientation, World destWorld) {
		this.worldsManager = worldsManager;
		this.parentWorld = parentWorld;
		this.position = Struct.malloc(Vector3.class).set(position);
		this.size = Struct.malloc(Vector2.class).set(size);
		this.orientation = Struct.malloc(Quaternion.class).set(orientation).normalize();
		
		this.destWorld = destWorld;
	}
	
	@Override
	protected void finalize() throws Throwable {
		try {
			Struct.free(position);
			Struct.free(size);
			Struct.free(orientation);
		} finally {
			super.finalize();
		}
	}
	
	public Camera getCamera() {
		return worldsManager.getCamera();
	}
	
	public World getParentWorld() {
		return parentWorld;
	}
	
	public World getDestWorld() {
		return destWorld;
	}
	
	@TakeStruct
	public Vector3 getPosition() {
		return position;
	}
	
	@TakeStruct
	public Vector2 getSize() {
		return size;
	}
	
	@TakeStruct
	public Quaternion getOrientation() {
		return orientation;
	}
	
	public Portal getDestPortal() {
		return destPortal;
	}
	
	public void setDestPortal(Portal destPortal) {
		this.destPortal = destPortal;
	}
	
	public void transform(Vector3 position, Quaternion orientation) {
		// Calculate the difference orientation between the portal's orientation and the camera's orientation
		Quaternion diff = new Quaternion(this.orientation).mult(new Quaternion(destPortal.getOrientation()).inverse()).normalize();
		// Multiply this difference with the destination portal to get the correct effect
		orientation.mult(diff).normalize();
		
		// Get the difference orientation between the origin portal and the destination portal
		diff.set(destPortal.getOrientation()).mult(new Quaternion(this.orientation).inverse()).normalize();
		
		// Convert the position difference using the difference orientation
		Vector3 diffPosition = new Vector3(position).sub(this.position);
		diff.mult3(diffPosition, position).add(destPortal.getPosition());
	}
	
	@Override
	public void update(long deltaTime) {
		Camera camera = getCamera();
		
		Vector3 delta = camera.getDelta();
		Vector3 prevPos = new Vector3(camera.getPosition()).sub(delta);
		
		Vector3 normal = orientation.mult3(Vector3.FORWARD, new Vector3()).normalize();
		
		float dot = delta.dot(normal);
		
		if(dot != 0.0) {
			float t = new Vector3(position).sub(prevPos).dot(normal) / dot;
			
			if(t >= 0f && t <= 1f) {
				Vector3 intersection = new Vector3(delta).mult(t).add(prevPos).sub(position);
				
				Quaternion inverse = new Quaternion(orientation).inverse().normalize();
				Vector3 offset = inverse.mult3(intersection, intersection);
				
				if(offset.x() >= 0f && offset.x() <= size.x() &&
				     offset.y() <= 0f && offset.y() >= -size.y()) {
					worldsManager.setWorld(destWorld);
					
					transform(camera.getPosition(), camera.getOrientation());
				}
			}
		}
	}
}
