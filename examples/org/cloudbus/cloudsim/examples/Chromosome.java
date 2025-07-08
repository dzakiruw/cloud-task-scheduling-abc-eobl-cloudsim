package org.cloudbus.cloudsim.examples;

/**
 * Represents a chromosome (individual) in the Genetic Algorithm.
 * Each chromosome contains a solution to the VM allocation problem.
 */
public class Chromosome {
    private int id; // Unique identifier for the chromosome
    private int[] genes; // The solution represented by this chromosome
    private double fitness; // Fitness value of this chromosome
    private double[] velocity; // Velocity for position updates (if needed)

    /**
     * Constructor with chromosome length
     * @param chromosomeLength The length of the chromosome
     */
    public Chromosome(int chromosomeLength) {
        this.id = -1; // Default ID if not provided
        this.genes = new int[chromosomeLength];
        this.fitness = 0.0;
        this.velocity = new double[chromosomeLength];
    }

    /**
     * Constructor with ID and chromosome
     * @param id The unique identifier for this chromosome
     * @param genes The chromosome genes (solution)
     */
    public Chromosome(int id, int[] genes) {
        this.id = id;
        this.genes = genes;
        this.fitness = 0.0;
        this.velocity = new double[genes.length];
    }

    /**
     * Get the ID of this chromosome
     * @return The chromosome ID
     */
    public int getId() {
        return id;
    }

    /**
     * Set the ID of this chromosome
     * @param id The new ID
     */
    public void setId(int id) {
        this.id = id;
    }

    /**
     * Get the genes of this chromosome
     * @return The chromosome genes
     */
    public int[] getGenes() {
        return genes;
    }

    /**
     * Set the genes of this chromosome
     * @param genes The new genes
     */
    public void setGenes(int[] genes) {
        this.genes = genes;
    }

    /**
     * Get the fitness value of this chromosome
     * @return The fitness value
     */
    public double getFitness() {
        return fitness;
    }

    /**
     * Set the fitness value of this chromosome
     * @param fitness The new fitness value
     */
    public void setFitness(double fitness) {
        this.fitness = fitness;
    }

    /**
     * Get the velocity array of this chromosome
     * @return The velocity array
     */
    public double[] getVelocity() {
        return velocity;
    }

    /**
     * Set the velocity for a specific index
     * @param index The index in the velocity array
     * @param value The new velocity value
     */
    public void setVelocity(int index, double value) {
        if (index >= 0 && index < velocity.length) {
            velocity[index] = value;
        } else {
            throw new IndexOutOfBoundsException("Index out of bounds for velocity array.");
        }
    }

    /**
     * Get a specific gene from the chromosome
     * @param index The index of the gene
     * @return The gene value
     */
    public int getGene(int index) {
        return genes[index];
    }

    /**
     * Set a specific gene in the chromosome
     * @param index The index of the gene
     * @param value The new gene value
     */
    public void setGene(int index, int value) {
        genes[index] = value;
    }

    /**
     * Get the length of the chromosome
     * @return The chromosome length
     */
    public int getChromosomeLength() {
        return genes.length;
    }

    /**
     * Clone this chromosome
     * @return A deep copy of this chromosome
     */
    @Override
    public Chromosome clone() {
        Chromosome clone = new Chromosome(this.id, this.genes.clone());
        clone.fitness = this.fitness;
        clone.velocity = this.velocity.clone();
        return clone;
    }
} 