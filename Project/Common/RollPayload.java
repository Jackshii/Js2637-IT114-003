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
    public void setSide(int diceside) {
        this.diceside = diceside;
    }
    @Override
    public String toString(){
        return super.toString() + String.format(" (%s,%s)",getdice(), getSide());
    }
}