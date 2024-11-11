<<<<<<< HEAD
//js2637 11/10/2024
//worked on it with my brother es525 from it114
=======
>>>>>>> ad90da3621efb4fcf09201613b99defc898de001
package Project.Common;

public class RollPayload extends Payload{
    private int numdice;
    private int diceside;
    public RollPayload(int numdice, int diceside){
        this.numdice = numdice;
        this.diceside = diceside;
    }
    public int getdice() {
        return numdice;
    }
    public void setdice(int numdice) {
        this.numdice = numdice;
    }
    public int getSide() {
        return diceside;
    }
<<<<<<< HEAD
    public void setSide(int diceside) {
=======
    public void setY(int diceside) {
>>>>>>> ad90da3621efb4fcf09201613b99defc898de001
        this.diceside = diceside;
    }
    @Override
    public String toString(){
        return super.toString() + String.format(" (%s,%s)",getdice(), getSide());
    }
}