package com.ra4king.fps.world;

import net.indiespot.struct.cp.Struct;
import net.indiespot.struct.cp.StructField;
import net.indiespot.struct.cp.StructType;
import net.indiespot.struct.cp.TakeStruct;

/**
 * @author Roi Atalla
 */
public class Chunk {
	public static final int CHUNK_CUBE_WIDTH = 32, CHUNK_CUBE_HEIGHT = 32, CHUNK_CUBE_DEPTH = 32;
	public static final int TOTAL_CUBES = Chunk.CHUNK_CUBE_WIDTH * Chunk.CHUNK_CUBE_HEIGHT * Chunk.CHUNK_CUBE_DEPTH;
	
	public static final float CUBE_SIZE = 2;
	public static final float SPACING = CUBE_SIZE; // cannot be less than CUBE_SIZE
	
	private ChunkModifiedCallback callback;
	
	private int cornerX, cornerY, cornerZ; // block indices
			
	// z * width * height + y * width + x
	private final Block[] blocks; // structured array
	
	private int cubeCount;
	
	private ChunkManager manager;
	
	public Chunk(ChunkManager manager, int cornerX, int cornerY, int cornerZ) {
		this.manager = manager;
		
		this.cornerX = cornerX;
		this.cornerY = cornerY;
		this.cornerZ = cornerZ;
		
		blocks = Struct.emptyArray(Block.class, CHUNK_CUBE_WIDTH * CHUNK_CUBE_HEIGHT * CHUNK_CUBE_DEPTH);
	}
	
	private void setupBlocks() {
		cubeCount = blocks.length;
		
		for(int i = 0; i < cubeCount; i++) {
			int rem = i % (CHUNK_CUBE_WIDTH * CHUNK_CUBE_HEIGHT);
			int x = rem % CHUNK_CUBE_WIDTH;
			int y = rem / CHUNK_CUBE_WIDTH;
			int z = i / (CHUNK_CUBE_WIDTH * CHUNK_CUBE_HEIGHT);
			
			blocks[i] = callback.chunkAdd(x, y, z, BlockType.AIR);
		}
	}
	
	public void setCallback(ChunkModifiedCallback callback) {
		this.callback = callback;
		setupBlocks();
	}
	
	public ChunkManager getChunkManager() {
		return manager;
	}
	
	public int getCornerX() {
		return cornerX;
	}
	
	public int getCornerY() {
		return cornerY;
	}
	
	public int getCornerZ() {
		return cornerZ;
	}
	
	public boolean cornerEquals(int cornerX, int cornerY, int cornerZ) {
		return this.cornerX == cornerX && this.cornerY == cornerY && this.cornerZ == cornerZ;
	}
	
	public boolean containsBlock(int x, int y, int z) {
		return cornerEquals((x / CHUNK_CUBE_WIDTH) * CHUNK_CUBE_WIDTH, (y / CHUNK_CUBE_HEIGHT) * CHUNK_CUBE_HEIGHT, (z / CHUNK_CUBE_DEPTH) * CHUNK_CUBE_DEPTH);
	}
	
	public int posToArrayIndex(int x, int y, int z) {
		return z * CHUNK_CUBE_WIDTH * CHUNK_CUBE_HEIGHT + y * CHUNK_CUBE_WIDTH + x;
	}
	
	public int getCubeCount() {
		return cubeCount;
	}
	
	public boolean isValidPos(int x, int y, int z) {
		return !(x < 0 || x >= CHUNK_CUBE_WIDTH || y < 0 || y >= CHUNK_CUBE_HEIGHT || z < 0 || z >= CHUNK_CUBE_DEPTH);
	}
	
	@TakeStruct
	public Block get(int x, int y, int z) {
		if(!isValidPos(x, y, z))
			return Struct.typedNull(Block.class);
		
		return blocks[posToArrayIndex(x, y, z)];
	}
	
	public Block[] getBlocks() {
		return blocks;
	}
	
	public void set(BlockType block, int x, int y, int z) {
		if(!isValidPos(x, y, z))
			throw new IllegalArgumentException("Invalid block position.");
		
		int i = posToArrayIndex(x, y, z);
		
		if(blocks[i].type == block.ordinal()) {
			return;
		}
		
		if(blocks[i] == Struct.typedNull(Block.class)) {
			blocks[i] = callback.chunkAdd(x, y, z, block);
		}
		
		if(block != BlockType.AIR) {
			if(blocks[i].type == BlockType.AIR.ordinal())
				cubeCount++;
		}
		else if(block.ordinal() != blocks[i].type) {
			cubeCount--;
		}
		
		blocks[i].type = block.ordinal();
		
		callback.chunkModified();
	}
	
	public void clearAll() {
		for(int x = 0; x < CHUNK_CUBE_WIDTH; x++) {
			for(int y = 0; y < CHUNK_CUBE_HEIGHT; y++) {
				for(int z = 0; z < CHUNK_CUBE_DEPTH; z++) {
					set(BlockType.AIR, x, y, z);
				}
			}
		}
	}
	
	public static interface ChunkModifiedCallback {
		Block chunkAdd(int x, int y, int z, BlockType block);
		
		void chunkModified();
	}
	
	public static enum BlockType {
		AIR, SOLID;
		
		public static BlockType[] values = values();
	}
	
	@StructType(sizeof = 16)
	public static class Block {
		@StructField(offset = 0)
		private int x;
		@StructField(offset = 4)
		private int y;
		@StructField(offset = 8)
		private int z;
		@StructField(offset = 12)
		private int type;
		
		@TakeStruct
		public Block init(Chunk chunk, int x, int y, int z, BlockType type) {
			this.x = chunk.cornerX + x;
			this.y = chunk.cornerY + y;
			this.z = chunk.cornerZ + z;
			this.type = type.ordinal();
			
			return this;
		}
		
		public int getX() {
			return x;
		}
		
		public int getY() {
			return y;
		}
		
		public int getZ() {
			return z;
		}
		
		public BlockType getType() {
			return BlockType.values[type];
		}
		
		public void setType(BlockType type) {
			this.type = type.ordinal();
		}
		
		public boolean equals(Block b) {
			return this.x == b.x && this.y == b.y && this.z == b.z;
		}
		
		public boolean isSurrounded(Chunk chunk) {
			Block up = chunk.getChunkManager().getBlock(x, y + 1, z);
			Block down = chunk.getChunkManager().getBlock(x, y - 1, z);
			Block left = chunk.getChunkManager().getBlock(x - 1, y, z);
			Block right = chunk.getChunkManager().getBlock(x + 1, y, z);
			Block front = chunk.getChunkManager().getBlock(x, y, z - 1);
			Block back = chunk.getChunkManager().getBlock(x, y, z + 1);
			
			int air = BlockType.AIR.ordinal();
			
			return up != null && up.type != air &&
					down != null && down.type != air &&
					left != null && left.type != air &&
					right != null && right.type != air &&
					front != null && front.type != air &&
					back != null && back.type != air;
		}
	}
}
