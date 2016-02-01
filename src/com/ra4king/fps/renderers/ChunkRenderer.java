package com.ra4king.fps.renderers;

import java.nio.ByteBuffer;

import org.lwjgl.BufferUtils;

import com.ra4king.fps.actors.Block;
import com.ra4king.fps.actors.Block.BlockType;
import com.ra4king.fps.renderers.WorldRenderer.DrawElementsIndirectCommand;
import com.ra4king.fps.world.Chunk;
import com.ra4king.fps.world.Chunk.ChunkModifiedCallback;
import com.ra4king.opengl.util.Stopwatch;
import com.ra4king.opengl.util.buffers.GLBuffer;
import com.ra4king.opengl.util.math.Vector3;
import com.ra4king.opengl.util.render.RenderUtils.FrustumCulling;

import net.indiespot.struct.cp.Struct;

public class ChunkRenderer implements ChunkModifiedCallback {
	private Chunk chunk;
	private GLBuffer glBuffer;
	private int chunkNumOffset;
	
	private ByteBuffer buffer;
	private Block[] compact;
	private int blockCount;
	
	private final int BUFFER_COUNT;
	private int uploadDirtyCount;
	
	public static final int CHUNK_DATA_SIZE = Chunk.TOTAL_BLOCKS * Struct.sizeof(Block.class);
	
	public ChunkRenderer(Chunk chunk, GLBuffer glBuffer, int bufferCount, int chunkNumOffset) {
		this.chunk = chunk;
		this.glBuffer = glBuffer;
		this.chunkNumOffset = chunkNumOffset;
		
		// map the Block array to a ByteBuffer
		buffer = BufferUtils.createByteBuffer(CHUNK_DATA_SIZE);
		compact = Struct.map(Block.class, buffer);
		
		chunk.setCallback(this);
		
		BUFFER_COUNT = bufferCount;
		uploadDirtyCount = BUFFER_COUNT;
	}
	
	public Chunk getChunk() {
		return chunk;
	}
	
	@Override
	public void chunkModified(Block block) {
		if(block != Struct.nullStruct(Block.class)) {
			if(!chunk.containsBlock(block)) {
				throw new IllegalArgumentException(String.format("Invalid block: (%d,%d,%d) of type %s. Chunk corner: (%d,%d,%d)",
				  block.getX(), block.getY(), block.getZ(), block.getType().toString(),
				  chunk.getCornerX(), chunk.getCornerY(), chunk.getCornerZ()));
			}
			
			testForSurface(block);
		}
		
		uploadDirtyCount = BUFFER_COUNT;
	}
	
	private void testForSurface(Block block) {
		Chunk neighbor;
		if(block.getX() == chunk.getCornerX()) {
			neighbor = chunk.getChunkManager().getChunkContaining(chunk.getCornerX() - Chunk.CHUNK_BLOCK_WIDTH, chunk.getCornerY(), chunk.getCornerZ());
			if(neighbor != null) {
				neighbor.getCallback().chunkModified(Struct.nullStruct(Block.class));
			}
		} else if(block.getX() == chunk.getCornerX() + Chunk.CHUNK_BLOCK_WIDTH - 1) {
			neighbor = chunk.getChunkManager().getChunkContaining(chunk.getCornerX() + Chunk.CHUNK_BLOCK_WIDTH, chunk.getCornerY(), chunk.getCornerZ());
			if(neighbor != null) {
				neighbor.getCallback().chunkModified(Struct.nullStruct(Block.class));
			}
		} else if(block.getY() == chunk.getCornerY()) {
			neighbor = chunk.getChunkManager().getChunkContaining(chunk.getCornerX(), chunk.getCornerY() - Chunk.CHUNK_BLOCK_HEIGHT, chunk.getCornerZ());
			if(neighbor != null) {
				neighbor.getCallback().chunkModified(Struct.nullStruct(Block.class));
			}
		} else if(block.getY() == chunk.getCornerY() + Chunk.CHUNK_BLOCK_HEIGHT - 1) {
			neighbor = chunk.getChunkManager().getChunkContaining(chunk.getCornerX(), chunk.getCornerY() + Chunk.CHUNK_BLOCK_HEIGHT, chunk.getCornerZ());
			if(neighbor != null) {
				neighbor.getCallback().chunkModified(Struct.nullStruct(Block.class));
			}
		} else if(block.getZ() == chunk.getCornerZ()) {
			neighbor = chunk.getChunkManager().getChunkContaining(chunk.getCornerX(), chunk.getCornerY(), chunk.getCornerZ() - Chunk.CHUNK_BLOCK_DEPTH);
			if(neighbor != null) {
				neighbor.getCallback().chunkModified(Struct.nullStruct(Block.class));
			}
		} else if(block.getZ() == chunk.getCornerZ() + Chunk.CHUNK_BLOCK_DEPTH - 1) {
			neighbor = chunk.getChunkManager().getChunkContaining(chunk.getCornerX(), chunk.getCornerY(), chunk.getCornerZ() + Chunk.CHUNK_BLOCK_DEPTH);
			if(neighbor != null) {
				neighbor.getCallback().chunkModified(Struct.nullStruct(Block.class));
			}
		}
	}
	
	public void update() {
		if(uploadDirtyCount == BUFFER_COUNT) {
			blockCount = 0;
			
			Stopwatch.start("Update Compact Array");
			
			for(Block block : chunk.getBlocks()) {
				if(block.getType() != BlockType.AIR && !block.isSurrounded(chunk)) {
					Struct.copy(Block.class, block, compact[blockCount++]);
				}
			}
			
			Stopwatch.stop();
		}
	}
	
	public int getLastCubeRenderCount() {
		return blockCount;
	}
	
	public boolean render(DrawElementsIndirectCommand command, FrustumCulling culling, int baseInstance) {
		int count = uploadDirtyCount;
		
		if(uploadDirtyCount > 0) {
			uploadDirtyCount--;
		}
		
		float halfSpacing = Chunk.SPACING * 0.5f;
		if(!culling.isRectPrismInsideFrustum(new Vector3(chunk.getCornerX(), chunk.getCornerY(), -chunk.getCornerZ())
		                                       .mult(Chunk.SPACING).sub(halfSpacing, halfSpacing, -halfSpacing),
		  Chunk.CHUNK_BLOCK_WIDTH * Chunk.SPACING,
		  Chunk.CHUNK_BLOCK_HEIGHT * Chunk.SPACING,
		  -Chunk.CHUNK_BLOCK_DEPTH * Chunk.SPACING)) {
			return false;
		}
		
		
		if(blockCount == 0) {
			return false;
		}
		
		Stopwatch.start("Upload VBO");
		
		if(count > 0) {
			final int DATA_SIZE = blockCount * Struct.sizeof(Block.class);
			
			ByteBuffer uploadBuffer = glBuffer.bind(chunkNumOffset * CHUNK_DATA_SIZE, DATA_SIZE);
			buffer.limit(DATA_SIZE).position(0);
			uploadBuffer.put(buffer);
			glBuffer.unbind();
		}
		
		Stopwatch.stop();
		
		command.instanceCount = blockCount;
		command.baseInstance = baseInstance + chunkNumOffset * Chunk.TOTAL_BLOCKS;
		
		return true;
	}
}
